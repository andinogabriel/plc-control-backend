# 0002 — Eventos/alarmas derivados (no una colección propia)

- **Estado:** Aceptada
- **Fecha:** 2026-06

## Contexto

El panel necesita un **registro de eventos y alarmas** (transiciones de estado, encendido/apagado del
cooler, sensor offline) con **reconocimiento (ACK)** y un conteo global de pendientes. La verdad del
sistema ya vive en la **serie de mediciones**: cada evento es una *transición* entre lecturas
consecutivas.

## Decisión

**No** persistir una colección de eventos. Los eventos se **derivan al vuelo** de la serie de
`Measurement` en el servidor y se **paginan** (el cliente recibe una página, no todo el histórico).
Lo único que se persiste es el **ACK** (`event_acks`), con un **id estable** por evento
(`<measurementId>-s|-c`, `offline-<id>`) para que sobreviva recargas y sea compartido entre clientes.

## Consecuencias

- **A favor:** el modelo se mantiene simple y sin doble fuente de verdad; no hay un camino de
  escritura de eventos que mantener sincronizado.
- El costo de derivación está **acotado por la ventana** consultada (se escanea ordenada).
- La alarma **SENSOR_OFFLINE** encaja en el mismo mecanismo: se inyecta como evento sintético cuando
  la última medición supera el umbral de inactividad (ver [ADR-0005](0005-sensor-offline-by-age.md)).
- **Escala futura:** si el volumen crece, se persistirían los eventos a medida que ocurren y se
  paginarían desde la base — sería un ADR nuevo que supersede a éste.
