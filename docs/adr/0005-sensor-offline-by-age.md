# 0005 — Sensor offline inferido por antigüedad del dato

- **Estado:** Aceptada
- **Fecha:** 2026-06

## Contexto

El backend es **pasivo** en la ingesta: solo guarda las mediciones que la Raspberry publica. Se
necesita saber si la Raspberry **se apagó o dejó de reportar**, para avisarlo aunque nadie tenga el
panel abierto, sin introducir un canal de heartbeat dedicado ni un scheduler complejo.

## Decisión

Inferir la vitalidad de la Raspberry a partir de la **antigüedad de la última medición** contra un
umbral configurable (`APP_SENSOR_OFFLINE_AFTER_SECONDS`, 1 h por defecto):

- `GET /api/measurements/status` expone `{ online, lastMeasurementAt, ageSeconds, offlineAfterSeconds }`.
- Se deriva una alarma **SENSOR_OFFLINE** (integrada en el modelo de eventos, ver
  [ADR-0002](0002-derived-events.md)) cuando la última lectura supera el umbral.

## Consecuencias

- **Sin heartbeat ni job programado dedicado:** menos piezas móviles; la condición se evalúa al
  consultar estado/eventos.
- El umbral es **configurable** por entorno (default alineado con el sensor: el DHT22 muestrea cada
  ~2 s, así que 1 h es holgado).
- La detección requiere que **alguien** consulte estado/eventos para "verlo"; para alertas activas
  (mail/webhook) haría falta un ADR nuevo con un scheduler.
