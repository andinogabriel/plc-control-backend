#!/usr/bin/env python3
"""
gateway.py  -  Gateway de I/O de campo para el sistema de control PLC (Camino 1).

Arquitectura purista: la LEY DE CONTROL vive en OpenPLC (programa ST con
histeresis + anti-ciclado). Este gateway NO decide nada: es solo el puente de
I/O que OpenPLC no puede hacer por si mismo.

Ciclo:
  1. Lee el DHT22 (GPIO4) via pigpio.
  2. Pide la config activa al backend (temperatureMax, hysteresisTemperature, ...).
  3. Escribe por Modbus TCP al PLC (holding registers %QW):
        HR0 Temp x10, HR1 Hum x10,
        HR2 umbral inferior (tmax - hyst) x10, HR3 umbral superior tmax x10,
        HR4 HumMin x10, HR5 HumMax x10.
  4. Lee del PLC la decision del controlador:
        coil 0  -> comando del cooler,   HR10 -> palabra de estado.
  5. Acciona el rele (GPIO14, activo-bajo) segun la bobina del PLC.
  6. Publica la medicion en el backend (POST /api/measurements).

Dependencias (todas ya presentes en la Raspberry, sin pip install):
    pigpio (+ pigpiod activo), requests, stdlib socket/struct.

Config por entorno:
    API_BASE   (def: https://plc-control-backend-production.up.railway.app)
    PLC_HOST   (def: 127.0.0.1)   PLC_PORT (def: 502)
    DHT_GPIO   (def: 4)           RELAY_GPIO (def: 14)  RELAY_ACTIVE_LOW (def: 0)
    MIN_INTERVAL (def: 4)
"""
import logging
import os
import signal
import socket
import struct
import time

import pigpio
import requests

API_BASE = os.environ.get(
    "API_BASE", "https://plc-control-backend-production.up.railway.app"
).rstrip("/")
PLC_HOST = os.environ.get("PLC_HOST", "127.0.0.1")
PLC_PORT = int(os.environ.get("PLC_PORT", "502"))
DHT_GPIO = int(os.environ.get("DHT_GPIO", "4"))
RELAY_GPIO = int(os.environ.get("RELAY_GPIO", "14"))
RELAY_ACTIVE_LOW = os.environ.get("RELAY_ACTIVE_LOW", "0") == "1"  # modulo ACTIVO-ALTO (verificado)

DEFAULT_INTERVAL = 30
MIN_INTERVAL = int(os.environ.get("MIN_INTERVAL", "4"))
MAX_BACKOFF = 300
HTTP_TIMEOUT = 5
SENSOR_RETRIES = 3

# Registros Modbus (holding registers = %QW, coil = %QX). Deben coincidir con el ST.
HR_TEMP, HR_HUM = 0, 1
HR_TMIN, HR_TMAX = 2, 3
HR_HMIN, HR_HMAX = 4, 5
HR_ESTADO = 10
COIL_COOLER = 0

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("gateway")

_running = True


def _handle_stop(signum, _frame):
    global _running
    log.info("Senal %s recibida; terminando de forma ordenada...", signum)
    _running = False


for _sig in (signal.SIGTERM, signal.SIGINT):
    signal.signal(_sig, _handle_stop)


# --------------------------------------------------------------------------------------------
# DHT22 (AM2302) via pigpio: decodifica 40 bits midiendo el ancho de cada pulso alto.
# --------------------------------------------------------------------------------------------
class DHT22:
    def __init__(self, pi, gpio):
        self.pi = pi
        self.gpio = gpio
        self._new = False
        self._temp = 0.0
        self._hum = 0.0
        self._high_tick = 0
        self._bit = 40
        self._hH = self._hL = self._tH = self._tL = self._cs = 0
        pi.set_pull_up_down(gpio, pigpio.PUD_OFF)
        pi.set_watchdog(gpio, 0)
        self._cb = pi.callback(gpio, pigpio.EITHER_EDGE, self._edge)

    def _edge(self, gpio, level, tick):
        if level == pigpio.TIMEOUT:
            self.pi.set_watchdog(self.gpio, 0)
            return
        if level == 1:
            diff = pigpio.tickDiff(self._high_tick, tick)
            self._high_tick = tick
            if diff > 250000:
                self._bit = -2
                self._hH = self._hL = self._tH = self._tL = self._cs = 0
            return
        diff = pigpio.tickDiff(self._high_tick, tick)
        val = 1 if diff >= 50 else 0
        b = self._bit
        if b < 0:
            pass
        elif b < 8:
            self._hH = (self._hH << 1) | val
        elif b < 16:
            self._hL = (self._hL << 1) | val
        elif b < 24:
            self._tH = (self._tH << 1) | val
        elif b < 32:
            self._tL = (self._tL << 1) | val
        elif b < 40:
            self._cs = (self._cs << 1) | val
        if b < 40:
            self._bit = b + 1
            if self._bit == 40:
                self._finish()

    def _finish(self):
        if ((self._hH + self._hL + self._tH + self._tL) & 0xFF) != self._cs:
            return
        self._hum = ((self._hH << 8) | self._hL) * 0.1
        th, tl = self._tH, self._tL
        if th & 0x80:
            self._temp = -(((th & 0x7F) << 8) | tl) * 0.1
        else:
            self._temp = ((th << 8) | tl) * 0.1
        self._new = True

    def read(self, settle=0.25):
        self._new = False
        self.pi.set_mode(self.gpio, pigpio.OUTPUT)
        self.pi.write(self.gpio, 0)
        time.sleep(0.018)
        self.pi.set_mode(self.gpio, pigpio.INPUT)
        self.pi.set_watchdog(self.gpio, 200)
        time.sleep(settle)
        self.pi.set_watchdog(self.gpio, 0)
        return (self._temp, self._hum) if self._new else None

    def cancel(self):
        self._cb.cancel()
        self.pi.set_watchdog(self.gpio, 0)


def read_sensor_with_retry(dht):
    for attempt in range(1, SENSOR_RETRIES + 1):
        r = dht.read()
        if r is not None:
            return r
        log.warning("Lectura DHT22 fallo (intento %d/%d)", attempt, SENSOR_RETRIES)
        time.sleep(2)
    return None


# --------------------------------------------------------------------------------------------
# Cliente Modbus TCP minimo (solo lo que necesitamos: FC01, FC03, FC16). Sin dependencias.
# --------------------------------------------------------------------------------------------
class ModbusTCP:
    def __init__(self, host, port, unit=1, timeout=3):
        self.host, self.port, self.unit, self.timeout = host, port, unit, timeout
        self.sock = None
        self._tid = 0

    def connect(self):
        self.close()
        self.sock = socket.create_connection((self.host, self.port), self.timeout)
        self.sock.settimeout(self.timeout)

    def close(self):
        if self.sock:
            try:
                self.sock.close()
            except OSError:
                pass
            self.sock = None

    def _txn(self, pdu):
        self._tid = (self._tid + 1) & 0xFFFF
        mbap = struct.pack(">HHHB", self._tid, 0, len(pdu) + 1, self.unit)
        self.sock.sendall(mbap + pdu)
        header = self._recv_exact(7)
        length = struct.unpack(">H", header[4:6])[0]
        resp = self._recv_exact(length - 1)
        func = resp[0]
        if func & 0x80:
            raise IOError("Modbus excepcion func=0x%02x code=%d" % (func, resp[1]))
        return resp

    def _recv_exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise IOError("conexion Modbus cerrada")
            buf += chunk
        return buf

    def write_registers(self, addr, values):
        data = b"".join(struct.pack(">H", v & 0xFFFF) for v in values)
        pdu = struct.pack(">BHHB", 0x10, addr, len(values), len(data)) + data
        self._txn(pdu)

    def read_holding(self, addr, count):
        resp = self._txn(struct.pack(">BHH", 0x03, addr, count))
        nbytes = resp[1]
        return list(struct.unpack(">" + "H" * (nbytes // 2), resp[2:2 + nbytes]))

    def read_coils(self, addr, count):
        resp = self._txn(struct.pack(">BHH", 0x01, addr, count))
        nbytes = resp[1]
        bits = []
        for i in range(count):
            bits.append(bool(resp[2 + i // 8] & (1 << (i % 8))))
        return bits


# --------------------------------------------------------------------------------------------
# Backend
# --------------------------------------------------------------------------------------------
session = requests.Session()


def get_active_config():
    try:
        r = session.get(f"{API_BASE}/api/config/latest", timeout=HTTP_TIMEOUT)
        if r.status_code == 200:
            return r.json()
        if r.status_code == 404:
            log.info("Todavia no hay config activa (cargala desde el panel).")
        else:
            log.warning("GET config devolvio %s", r.status_code)
    except requests.RequestException as e:
        log.warning("No se pudo obtener config: %s", e)
    return None


def compute_status(temp, hum, cfg):
    tmax, tmin = cfg["temperatureMax"], cfg["temperatureMin"]
    hyst = cfg.get("hysteresisTemperature", 0.0)
    if temp > tmax + hyst or temp < tmin - hyst:
        return "CRITICAL"
    if temp > tmax or temp < tmin:
        return "WARNING_TEMP"
    if hum > cfg["humidityMax"] or hum < cfg["humidityMin"]:
        return "WARNING_HUMIDITY"
    return "NORMAL"


def publish_measurement(temp, hum, cooler_on, status):
    payload = {
        "temperature": temp,
        "humidity": hum,
        "coolerOn": cooler_on,
        "relayOn": cooler_on,
        "status": status,
    }
    try:
        r = session.post(f"{API_BASE}/api/measurements", json=payload, timeout=HTTP_TIMEOUT)
        if r.status_code == 429:
            log.warning("Rate limited (429); backoff.")
            return "rate_limited"
        if r.status_code >= 400:
            log.error("POST medicion %s -> %s", r.status_code, r.text[:200])
            return "error"
        return "ok"
    except requests.RequestException as e:
        log.error("No se pudo publicar la medicion: %s", e)
        return "error"


# --------------------------------------------------------------------------------------------
# Rele (GPIO14, activo-bajo)
# --------------------------------------------------------------------------------------------
def relay_level(on):
    if RELAY_ACTIVE_LOW:
        return 0 if on else 1
    return 1 if on else 0


def _sleep(seconds):
    end = time.monotonic() + seconds
    while _running and time.monotonic() < end:
        time.sleep(min(1.0, end - time.monotonic()))


def main():
    log.info("Gateway iniciado. API_BASE=%s  PLC=%s:%d", API_BASE, PLC_HOST, PLC_PORT)
    pi = pigpio.pi()
    if not pi.connected:
        raise SystemExit("ERROR: no conecta a pigpiod (sudo systemctl start pigpiod)")
    dht = DHT22(pi, DHT_GPIO)
    pi.set_mode(RELAY_GPIO, pigpio.OUTPUT)
    pi.write(RELAY_GPIO, relay_level(False))  # arranca apagado

    plc = ModbusTCP(PLC_HOST, PLC_PORT)
    backoff = 0
    try:
        while _running:
            reading = read_sensor_with_retry(dht)
            cfg = get_active_config()

            if reading is None:
                log.error("Sin lectura de sensor; se omite este ciclo.")
            else:
                temp, hum = reading
                try:
                    if plc.sock is None:
                        plc.connect()
                    # 1) inyectar temp/hum al PLC
                    regs = {HR_TEMP: round(temp * 10), HR_HUM: round(hum * 10)}
                    # 2) traducir la config del backend a los dos umbrales del controlador
                    if cfg:
                        tmax = cfg["temperatureMax"]
                        hyst = cfg.get("hysteresisTemperature", 0.0)
                        regs[HR_TMAX] = round(tmax * 10)
                        regs[HR_TMIN] = round((tmax - hyst) * 10)
                        regs[HR_HMIN] = round(cfg["humidityMin"] * 10)
                        regs[HR_HMAX] = round(cfg["humidityMax"] * 10)
                    # escribir el bloque contiguo HR0..HR5 (o HR0..HR1 si no hay config)
                    hi = HR_HMAX if cfg else HR_HUM
                    plc.write_registers(0, [regs.get(a, 0) for a in range(0, hi + 1)])
                    # 3) leer la DECISION del PLC (control corre en OpenPLC)
                    cooler_on = plc.read_coils(COIL_COOLER, 1)[0]
                    estado = plc.read_holding(HR_ESTADO, 1)[0]
                except (OSError, IOError) as e:
                    log.error("Modbus con el PLC fallo: %s", e)
                    plc.close()
                    _sleep(2)
                    continue

                # 4) accionar el rele segun el PLC
                pi.write(RELAY_GPIO, relay_level(cooler_on))

                # 5) publicar
                status = compute_status(temp, hum, cfg) if cfg else "NORMAL"
                estado_txt = ["reposo", "enfriando", "anti-ciclado", "fail-safe"][estado] if estado < 4 else str(estado)
                log.info("T=%.1fC H=%.1f%% | PLC cooler=%s estado=%s | status=%s",
                         temp, hum, "ON" if cooler_on else "OFF", estado_txt, status)
                result = publish_measurement(temp, hum, cooler_on, status)
                if result == "ok":
                    backoff = 0
                elif result == "rate_limited":
                    backoff = min(max(backoff * 2, 30), MAX_BACKOFF)
                else:
                    backoff = min(max(backoff * 2, 10), MAX_BACKOFF)

            interval = MIN_INTERVAL
            if cfg:
                interval = max(int(cfg.get("measurementIntervalSeconds", DEFAULT_INTERVAL)), MIN_INTERVAL)
            _sleep(interval + backoff)
    finally:
        log.info("Apagando rele/cooler antes de salir.")
        pi.write(RELAY_GPIO, relay_level(False))
        dht.cancel()
        plc.close()
        pi.stop()


if __name__ == "__main__":
    main()
