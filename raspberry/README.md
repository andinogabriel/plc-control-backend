# Raspberry Pi — implementación de control (Camino 1)

Implementación **purista** del lazo de control del sistema de temperatura/humedad. La
**ley de control vive íntegramente en OpenPLC** (IEC 61131-3); la Raspberry solo aporta la
I/O de campo que un PLC no puede hacer por sí mismo.

## Arquitectura

```
   DHT22 (GPIO4)                                   Relé/Cooler (GPIO14, activo-alto)
        │  temp/hum                                        ▲ comando
        ▼                                                  │
   ┌─────────────────────────  gateway.py  ─────────────────────────┐
   │  (solo I/O: NO decide)                                          │
   │   1. lee DHT22 (pigpio)                                         │
   │   2. escribe temp/hum + umbrales  ──Modbus TCP──►  OpenPLC :502 │
   │   3. lee la bobina del cooler     ◄──Modbus TCP──  (control ST) │
   │   4. acciona el relé según el PLC                               │
   │   5. publica la medición  ──HTTPS──►  backend (Railway)         │
   └────────────────────────────────────────────────────────────────┘
```

El controlador **decide** dentro de OpenPLC. El gateway nunca calcula si el cooler debe
encender: lee esa decisión del PLC. Es equivalente a un PLC real con módulos de I/O remota.

## Ley de control — `control_histeresis.st`

Control **on-off con banda muerta (histéresis)** sobre un actuador binario (el relé), que es
la ley correcta para este caso (un relé no modula: PID no aplica). Extras que lo hacen
robusto como control real:

- **Banda muerta** `[TempMin, TempMax]`: enciende al llegar a `TempMax`, no apaga hasta bajar
  a `TempMin`. Absorbe el ruido de medición y evita el *chattering* del on-off sin memoria.
- **Anti-ciclado**: tiempo mínimo encendido y apagado (15 s, `TON`) para proteger el relé de
  ciclos demasiado cortos.
- **Fail-safe**: si `TempMax <= TempMin` (config inválida o aún no escrita) el cooler queda
  apagado.
- **Palabra de estado** (`Estado`) para telemetría: `0` reposo · `1` enfriando ·
  `2` espera anti-ciclado · `3` fail-safe.

El backend define el lazo como *ON en `temperatureMax`, OFF en `temperatureMax − hysteresisTemperature`*.
El gateway **traduce** esa config a los dos umbrales del controlador genérico:
`TempMax_thr = temperatureMax`, `TempMin_thr = temperatureMax − hysteresisTemperature`.

## Contrato Modbus (OpenPLC = esclavo TCP :502, gateway = maestro)

| Registro | Dirección | Sentido | Contenido |
|---|---|---|---|
| `%QW0`  | HR 0  | gateway → PLC | Temperatura ×10 (signed) |
| `%QW1`  | HR 1  | gateway → PLC | Humedad ×10 |
| `%QW2`  | HR 2  | gateway → PLC | Umbral inferior (apaga) ×10 |
| `%QW3`  | HR 3  | gateway → PLC | Umbral superior (enciende) ×10 |
| `%QW4`  | HR 4  | gateway → PLC | HumMin ×10 (monitoreo) |
| `%QW5`  | HR 5  | gateway → PLC | HumMax ×10 (monitoreo) |
| `%QX0.0`| Coil 0| PLC → gateway | Comando del cooler |
| `%QW10` | HR 10 | PLC → gateway | Palabra de estado |

## Puesta en marcha

**OpenPLC** (web UI en `http://<ip-pi>:8080`, admin `openplc/openplc`):
1. Hardware → *Blank Linux* (Camino 1: OpenPLC no toca GPIO).
2. Settings → *Enable Modbus Server* en 502, y *Start OpenPLC in RUN mode* (autostart tras reboot).
3. Programs → subir `control_histeresis.st` → *Launch program*.

**Gateway** (servicio systemd desatendido):
```bash
cp gateway.py /home/admin/gateway.py
cp gateway.env.example /home/admin/gateway.env      # editar API_BASE si hace falta
sudo cp control-gateway.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now control-gateway.service
journalctl -u control-gateway -f                    # logs en vivo
```

Requisitos ya presentes en Raspberry Pi OS: `pigpiod` (activo), `python3-pigpio`, `requests`.
El gateway **no necesita `pip install`** (Modbus va con `socket` de stdlib).

## Cableado

- **DHT22 (AM2302):** VCC → pin 1 (3.3 V) · DATA → pin 7 (GPIO4) · GND → pin 9.
- **Relé (JQC-3FF-S-Z, activo-alto):** VCC → pin 2 (5 V) · GND → pin 6 · IN → pin 8 (GPIO14).

## Notas de MatIEC (para futuras ediciones del `.st`)

El compilador de OpenPLC (MatIEC) es estricto:
- Variables *located* (`AT %…`) y no-located deben ir en **bloques `VAR` separados**.
- Las instancias de function block (`TON`) van en **su propio bloque `VAR`**.
- Los identificadores son **case-insensitive**: no nombres una variable `tOn` (colisiona con el
  FB `TON`). Por eso los timers se llaman `TimerOn`/`TimerOff`.
