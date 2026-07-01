# 0001 — MongoDB como almacén de documentos

- **Estado:** Aceptada
- **Fecha:** 2026-06

## Contexto

El sistema persiste dos cosas: **configuraciones versionadas** (umbrales, histéresis, intervalo, con
auditoría de quién/cuándo) y una **serie temporal de mediciones**. El esquema es simple y de forma
naturalmente documental; no hay relaciones complejas ni necesidad de JOINs. Es un proyecto académico
con presupuesto cero para infraestructura.

## Decisión

Usar **MongoDB** (base de datos de documentos) con Spring Data MongoDB. Tres colecciones: `configs`,
`measurements`, `event_acks`.

## Consecuencias

- **A favor:** esquema flexible, mapeo directo a los DTOs, y un tier gestionado **gratuito**
  (Atlas M0) que evita administrar una base propia.
- **Sin transacciones multi-documento** en un despliegue standalone (M0). El invariante de
  "una sola config activa" se resuelve con un **índice parcial único** sobre `active` + reintento
  ante colisión, en vez de una transacción.
- **Sin claves foráneas:** las relaciones (p. ej. `Config → Measurement`) son **lógicas**, no
  físicas (ver el diagrama de clases). La `Config` activa parametriza la evaluación de cada lectura.
- La **retención** de mediciones se implementa con un **índice TTL** (`APP_RETENTION_MEASUREMENT_DAYS`),
  que además sirve para los filtros/orden por fecha.
