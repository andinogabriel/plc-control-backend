# Load tests (k6)

Load scenarios for the backend, written for [k6](https://k6.io). They are **not** part of CI:
they need a running backend and the `k6` binary installed locally.

## Requisitos

- Backend corriendo (por ejemplo `docker compose up`), accesible en `http://localhost:8080`.
- `k6` instalado ([guía de instalación](https://grafana.com/docs/k6/latest/set-up/install-k6/)).

## Correr

```bash
k6 run tests/load/measurements.js

# contra otro host:
BASE_URL=https://mi-backend k6 run tests/load/measurements.js
```

## Qué mide

`measurements.js` ejercita el camino de **lectura** (el de alto volumen: el tablero y el
kiosco hacen polling). Sube de 1 a 50 usuarios virtuales y golpea
`GET /api/measurements/latest` y `GET /api/measurements`.

El objetivo es **robustez, no throughput crudo**: bajo carga sostenida el sistema debe degradar
de forma controlada. Por eso:

- `200`, `404` (sin datos) y `429` (el rate limiter haciendo su trabajo) cuentan como **sanos**;
- solo `5xx` y errores de conexión cuentan como fallas reales.

### Thresholds (la corrida falla si no se cumplen)

| Métrica | Umbral | Significado |
| --- | --- | --- |
| `server_errors` | `rate < 0.01` | menos del 1% de respuestas son `5xx` / error de red |
| `http_req_duration` | `p(95) < 800ms` | el percentil 95 de latencia se mantiene acotado |

> Nota: como el rate limiting es por IP, una corrida desde una sola máquina va a recibir muchos
> `429` apenas supera los topes configurados (`app.rate-limit.*`). Eso es esperado y es
> justamente la protección anti-abuso funcionando, no un fallo de la prueba.
