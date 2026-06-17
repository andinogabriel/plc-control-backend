# scripts/ — datos de prueba sin la Raspberry

Dos utilidades para tener datos realistas mientras el dispositivo físico **no está conectado**:
una rellena el **histórico** y la otra emula la Raspberry **en vivo**. Son complementarias y
ninguna toca el código del backend (no hace falta `docker build` para usarlas).

> ¿Y el seed automático? El stack ya trae un seed inicial (`docker/mongo-init/`) que corre **solo
> con la base vacía** (`docker compose down -v && up`). Estos scripts son para reseedear o simular
> **sin borrar el volumen**, con la base ya levantada.

| Script | Para qué | Cómo escribe |
| --- | --- | --- |
| `seed-measurements.mongo.js` | Backfill de **histórico** (rangos largos del tablero con datos) | Mongo directo (`mongosh`) |
| `simulate-raspberry.mjs` | Flujo **en vivo** (badge "en línea", charts moviéndose, cooler reaccionando) | `POST /api/measurements` |

**Requisitos:** contenedor de Mongo (`control-mongodb`) corriendo y una **configuración activa**
(creala desde el panel o con `POST /api/config`). Para el simulador, además el backend en `:8080`.

---

## 1. `seed-measurements.mongo.js` — histórico

La API **no** puede backfillear: el `createdAt` lo pone el servidor (`@CreatedDate`), así que un
`POST` siempre cae "ahora". Para que los rangos largos (24 h / 7 d / 30 d) tengan datos, este
script inserta una serie temporal directo en MongoDB, terminando **ahora**:

- curva sinusoidal diaria de temperatura/humedad cuyos picos cruzan la banda;
- cooler con la **misma histéresis** del controlador (estado arrastrado);
- `status` derivado de la banda (`NORMAL` / `WARNING_TEMP` / `WARNING_HUMIDITY` / `CRITICAL`).

```powershell
docker cp scripts/seed-measurements.mongo.js control-mongodb:/tmp/seed.js
docker exec control-mongodb mongosh controlsystem --quiet --file /tmp/seed.js
```

Salida esperada:

```
Removed N existing measurement(s).
Inserted 2881 measurements from <hace ~10 días> to <ahora>
Config used: T[18-29] H[31-65] hystT=2
```

Tunables (constantes arriba del script): `DAYS` (default 10), `INTERVAL_SECONDS` (default 300) y
`REPLACE` (default `true`: borra las mediciones existentes antes de insertar, así re-correrlo es
idempotente).

> ⚠️ **Usá PowerShell o CMD, no Git Bash.** Git Bash convierte `/tmp/seed.js` a una ruta de
> Windows y falla. Si usás Git Bash sí o sí, antepoené `MSYS_NO_PATHCONV=1` a cada comando.

---

## 2. `simulate-raspberry.mjs` — en vivo

Hace de Raspberry: cada `INTERVAL_MS` lee la config activa, calcula una medición y la postea a
`/api/measurements` (ese endpoint no pide API key). Modela el **lazo de control real** —el calor
ambiente sube la temperatura, el cooler la baja con histéresis— así que la lectura hace
dientes-de-sierra dentro de la banda. Necesita **Node 18+** (usa `fetch` global).

```bash
node scripts/simulate-raspberry.mjs                 # lazo de control (default)
INTERVAL_MS=3000 node scripts/simulate-raspberry.mjs
```

`SCENARIO` fuerza un estado a demanda (p. ej. para mostrar la **alerta roja** en la defensa):

```bash
SCENARIO=critical          node scripts/simulate-raspberry.mjs   # CRITICAL (rojo)
SCENARIO=warning-temp      node scripts/simulate-raspberry.mjs
SCENARIO=warning-humidity  node scripts/simulate-raspberry.mjs
SCENARIO=normal            node scripts/simulate-raspberry.mjs
```

Frená con `Ctrl+C`. Postea ~1 lectura por `INTERVAL_MS`; el cap del backend es **30/min por IP**,
así que no bajes mucho de ~2 s (si te pasás, el script loguea `HTTP 429` y sigue).

| Variable | Default | Para qué |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | API del backend |
| `INTERVAL_MS` | `5000` | cada cuánto postea |
| `SCENARIO` | `normal` | `normal` \| `warning-temp` \| `warning-humidity` \| `critical` |

---

## Flujo típico de demo

```powershell
docker compose up -d                                            # 1. stack (Mongo + app) — una vez
docker cp scripts/seed-measurements.mongo.js control-mongodb:/tmp/seed.js
docker exec control-mongodb mongosh controlsystem --quiet --file /tmp/seed.js   # 2. histórico
node scripts/simulate-raspberry.mjs                            # 3. vivo (otra terminal)
# 4. abrir el panel; para la alerta roja: SCENARIO=critical node scripts/simulate-raspberry.mjs
```
