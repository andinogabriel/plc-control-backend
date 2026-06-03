"""
Ejemplo de cliente para la Raspberry Pi 3B+.

Lee el sensor DHT, aplica la lógica de control con histéresis (consultando la config
activa del backend), activa el relay/cooler vía GPIO y publica la medición.

Este script es ilustrativo: en el sistema real, OpenPLC puede encargarse de la lógica
de control y este script solo publicar mediciones. Se incluye la versión "todo en Python"
para que el ciclo sea defendible de punta a punta sin OpenPLC.

Dependencias:
    pip install requests adafruit-circuitpython-dht RPi.GPIO
"""

import time
import requests

# import board
# import adafruit_dht
# import RPi.GPIO as GPIO

API_BASE = "http://localhost:8080"
RELAY_PIN = 17
READ_INTERVAL_SECONDS = 10

# --- Simulación de hardware (reemplazar por lectura real del DHT y GPIO) ---
def read_sensor():
    """Devuelve (temperatura_c, humedad_pct). Reemplazar por adafruit_dht."""
    import random
    return round(random.uniform(20, 30), 1), round(random.uniform(35, 65), 1)


def set_relay(on: bool):
    """Activa/desactiva el relay. Reemplazar por GPIO.output(RELAY_PIN, ...)."""
    print(f"[RELAY] {'ON' if on else 'OFF'}")


def get_active_config():
    try:
        r = requests.get(f"{API_BASE}/api/config/latest", timeout=5)
        if r.status_code == 200:
            return r.json()
    except requests.RequestException as e:
        print(f"[WARN] no se pudo obtener config: {e}")
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
    payload = {
        "temperature": temp,
        "humidity": hum,
        "coolerOn": cooler_on,
        "relayOn": relay_on,
        "status": status,
    }
    try:
        r = requests.post(f"{API_BASE}/api/measurements", json=payload, timeout=5)
        print(f"[POST] {r.status_code} -> {payload}")
    except requests.RequestException as e:
        print(f"[ERROR] no se pudo publicar la medición: {e}")


def main():
    cooler_on = False
    while True:
        temp, hum = read_sensor()
        config = get_active_config()

        if config:
            cooler_on = decide_cooler(temp, config, cooler_on)
            status = compute_status(temp, hum, config)
        else:
            status = "NORMAL"

        set_relay(cooler_on)
        publish_measurement(temp, hum, cooler_on, cooler_on, status)
        time.sleep(READ_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()
