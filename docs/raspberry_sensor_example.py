"""
Cliente para la Raspberry Pi 3B+ (gateway de adquisición).

Lee el sensor DHT, aplica la lógica de control con histéresis (consultando la config
activa del backend), activa el relay/cooler vía GPIO y publica la medición.

Este script es ilustrativo en su capa de hardware (read_sensor / set_relay son simuladas),
pero el bucle de operación está pensado para correr desatendido en la Raspberry: maneja
caídas del backend, rate limiting, lecturas fallidas del sensor y apagado seguro.

Dependencias:
    pip install requests adafruit-circuitpython-dht RPi.GPIO

Configuración por entorno (para apuntar al backend en la nube, p. ej. Railway):
    export API_BASE="https://tu-backend.up.railway.app"   # sin barra final
    # Solo si el backend tiene APP_CONFIG_API_KEY (la Raspberry no la necesita para
    # POST /api/measurements; sí haría falta si publicara config):
    export CONFIG_API_KEY="..."

Para correr desatendido (autostart + auto-restart + apagado seguro), instalalo como servicio
systemd: ver control-gateway.service y gateway.env.example en esta misma carpeta.
"""

import logging
import os
import signal
import time

import requests

# import board
# import adafruit_dht
# import RPi.GPIO as GPIO

# --- Configuración por entorno ---------------------------------------------------------------
# Base URL del backend. En dev cae a localhost; en producción seteá API_BASE a la URL pública
# HTTPS del backend (Railway). Se quita la barra final para no formar URLs con "//".
API_BASE = os.environ.get("API_BASE", "http://localhost:8080").rstrip("/")
# Opcional: solo si este cliente llegara a publicar config (publicar mediciones no lo requiere).
CONFIG_API_KEY = os.environ.get("CONFIG_API_KEY", "")
RELAY_PIN = 17

# Cadencia de fallback hasta obtener la primera config; la real la marca measurementIntervalSeconds.
DEFAULT_INTERVAL_SECONDS = 30
# Piso de intervalo: aunque la config pida algo más chico, no publicamos más seguido que esto, para
# no chocar con el rate limiting del backend (mide ~30 req/min por IP en /api/measurements).
MIN_INTERVAL_SECONDS = 5
# Tope del backoff extra cuando el backend está caído o nos limita (no martillar / no quedar baneado).
MAX_BACKOFF_SECONDS = 300
# Reintentos de lectura del DHT (es común que falle por CRC/timeout y ande al segundo intento).
SENSOR_READ_RETRIES = 3
HTTP_TIMEOUT_SECONDS = 5

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("gateway")

# Una sola conexión HTTP (keep-alive) reutilizada en todo el bucle.
session = requests.Session()
if CONFIG_API_KEY:
    session.headers["X-Api-Key"] = CONFIG_API_KEY

# Flag de parada: SIGTERM (systemctl stop) o SIGINT (Ctrl-C) lo bajan para salir limpio.
_running = True


def _handle_stop(signum, _frame):
    global _running
    log.info("Señal %s recibida; terminando de forma ordenada…", signum)
    _running = False


for _sig in (signal.SIGTERM, signal.SIGINT):
    signal.signal(_sig, _handle_stop)


# --- Simulación de hardware (reemplazar por lectura real del DHT y GPIO) ----------------------
def read_sensor():
    """Devuelve (temperatura_c, humedad_pct). Reemplazar por adafruit_dht."""
    import random
    return round(random.uniform(20, 30), 1), round(random.uniform(35, 65), 1)


def set_relay(on: bool):
    """Activa/desactiva el relay. Reemplazar por GPIO.output(RELAY_PIN, ...)."""
    log.info("[RELAY] %s", "ON" if on else "OFF")


# --- Adquisición y control --------------------------------------------------------------------
def read_sensor_with_retry():
    """Lee el sensor con reintentos. Devuelve (temp, hum) o (None, None) si falla siempre."""
    for attempt in range(1, SENSOR_READ_RETRIES + 1):
        try:
            temp, hum = read_sensor()
            if temp is None or hum is None:
                raise ValueError("lectura vacía")
            return temp, hum
        except Exception as e:  # noqa: BLE001 - el DHT lanza varias excepciones distintas
            log.warning("Lectura de sensor falló (intento %d/%d): %s", attempt, SENSOR_READ_RETRIES, e)
            _interruptible_sleep(1)
    return None, None


def get_active_config():
    """Obtiene la config activa, o None si no hay o el backend no responde."""
    try:
        r = session.get(f"{API_BASE}/api/config/latest", timeout=HTTP_TIMEOUT_SECONDS)
        if r.status_code == 200:
            return r.json()
        if r.status_code == 404:
            log.info("Todavía no hay config activa (cargala desde el panel).")
        else:
            log.warning("GET config devolvió %s", r.status_code)
    except requests.RequestException as e:
        log.warning("No se pudo obtener config: %s", e)
    return None


def decide_cooler(temp, config, currently_on):
    """Control on/off con histéresis: enciende al pasar max, apaga al bajar de max - histeresis."""
    t_max = config["temperatureMax"]
    hyst = config["hysteresisTemperature"]
    if temp >= t_max:
        return True
    if temp <= t_max - hyst:
        return False
    return currently_on  # zona muerta: mantener estado


def compute_status(temp, hum, config):
    if (temp > config["temperatureMax"] + config["hysteresisTemperature"]
            or temp < config["temperatureMin"] - config["hysteresisTemperature"]):
        return "CRITICAL"
    if temp > config["temperatureMax"] or temp < config["temperatureMin"]:
        return "WARNING_TEMP"
    if hum > config["humidityMax"] or hum < config["humidityMin"]:
        return "WARNING_HUMIDITY"
    return "NORMAL"


def publish_measurement(temp, hum, cooler_on, relay_on, status):
    """Publica la medición. Devuelve 'ok', 'rate_limited' o 'error'."""
    payload = {
        "temperature": temp,
        "humidity": hum,
        "coolerOn": cooler_on,
        "relayOn": relay_on,
        "status": status,
    }
    try:
        r = session.post(f"{API_BASE}/api/measurements", json=payload, timeout=HTTP_TIMEOUT_SECONDS)
        if r.status_code == 429:
            log.warning("Rate limited (429) al publicar; se aplicará backoff.")
            return "rate_limited"
        if r.status_code >= 400:
            # 400 = payload inválido (p. ej. valores fuera de rango): se loguea y se sigue.
            log.error("POST medición devolvió %s -> %s", r.status_code, r.text[:200])
            return "error"
        log.info("Medición publicada %s -> %s", r.status_code, payload)
        return "ok"
    except requests.RequestException as e:
        log.error("No se pudo publicar la medición: %s", e)
        return "error"


def _interruptible_sleep(seconds):
    """Duerme en tramos cortos para reaccionar rápido a una señal de parada."""
    end = time.monotonic() + seconds
    while _running:
        remaining = end - time.monotonic()
        if remaining <= 0:
            break
        time.sleep(min(1.0, remaining))


def main():
    log.info("Gateway iniciado. API_BASE=%s", API_BASE)
    cooler_on = False
    backoff = 0
    try:
        while _running:
            temp, hum = read_sensor_with_retry()
            config = get_active_config()

            if temp is None:
                # Sin lectura válida no inventamos un valor: se omite el ciclo (no se toca el relay).
                log.error("Sin lectura de sensor; se omite este ciclo.")
            else:
                if config:
                    cooler_on = decide_cooler(temp, config, cooler_on)
                    status = compute_status(temp, hum, config)
                else:
                    status = "NORMAL"

                set_relay(cooler_on)
                result = publish_measurement(temp, hum, cooler_on, cooler_on, status)
                if result == "ok":
                    backoff = 0
                elif result == "rate_limited":
                    backoff = min(max(backoff * 2, 30), MAX_BACKOFF_SECONDS)
                else:
                    backoff = min(max(backoff * 2, 10), MAX_BACKOFF_SECONDS)

            interval = DEFAULT_INTERVAL_SECONDS
            if config:
                interval = max(int(config.get("measurementIntervalSeconds", DEFAULT_INTERVAL_SECONDS)),
                               MIN_INTERVAL_SECONDS)
            _interruptible_sleep(interval + backoff)
    finally:
        # Seguridad física: nunca dejar el cooler encendido si el gateway se detiene.
        log.info("Apagando relay/cooler antes de salir.")
        set_relay(False)
        # GPIO.cleanup() en hardware real.


if __name__ == "__main__":
    main()
