# 0004 — Despliegue gestionado (Atlas + Railway + Cloudflare Pages)

- **Estado:** Aceptada
- **Fecha:** 2026-06

## Contexto

Para la demo, la **Raspberry Pi y el panel** deben consultar la API por una **URL pública HTTPS**,
sin montar servidores propios ni depender de la conexión de casa. Presupuesto: gratis o muy barato,
por uno o dos meses.

## Decisión

Separar las tres piezas en servicios gestionados:

- **MongoDB → Atlas M0** (gratis).
- **Backend → Railway** (build desde el `Dockerfile`, ~US$5/mes).
- **Frontend → Cloudflare Pages** (gratis, build estático + CDN).
- La **Raspberry** apunta a la URL pública del backend.

## Consecuencias

- Costo total ~**US$5/mes**, cancelable al terminar; nada queda encendido en casa.
- **Auto-deploy** en cada push a `main` (Railway y Pages), sin pasos manuales.
- Requiere cuidar **variables de entorno** por servicio, el **CORS** (origen del panel) y la lista
  de IP de Atlas (`0.0.0.0/0`). Todo documentado en [`docs/DEPLOYMENT.md`](../DEPLOYMENT.md).
- El backend debe escuchar en el `$PORT` inyectado y respetar `X-Forwarded-*` detrás del proxy.
