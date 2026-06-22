# Backend — Sistema de Control PLC

Backend REST para un sistema de control de temperatura/humedad usando Raspberry Pi 3B+, sensor DHT, OpenPLC, relay, cooler, MongoDB y frontend React.

Java 25 · Spring Boot 3.5 · Spring Data MongoDB · Gradle · arquitectura por capas.

## Contenido

- [Qué es y para qué sirve](#qué-es-y-para-qué-sirve)
- [Arquitectura general](#arquitectura-general)
- [Responsabilidad de cada componente](#responsabilidad-de-cada-componente)
- [Casos de uso](#casos-de-uso)
- [Flujo principal del sistema](#flujo-principal-del-sistema)
- [Qué hace el sistema (diagrama de secuencia)](#qué-hace-el-sistema-diagrama-de-secuencia)
- [Rol de OpenPLC](#rol-de-openplc)
- [Integración con Modbus TCP](#integración-con-modbus-tcp)
- [Lógica de control](#lógica-de-control)
- [Máquina de estados](#máquina-de-estados)
- [Modelo de configuración](#modelo-de-configuración)
- [Modelo de medición](#modelo-de-medición)
- [Eventos y alarmas (derivados)](#eventos-y-alarmas-derivados)
- [Modelo de datos (UML)](#modelo-de-datos-uml)
- [Arquitectura por capas (UML)](#arquitectura-por-capas-uml)
- [Seguridad y anti-abuso](#seguridad-y-anti-abuso)
- [Tiempo real (SSE)](#tiempo-real-sse)
- [Ejecutar con Docker](#ejecutar-con-docker)
- [Variables de entorno](#variables-de-entorno)
- [Datos de prueba](#datos-de-prueba)
- [Reconstruir / limpiar (Docker)](#reconstruir--limpiar-docker)
- [Documentación de la API](#documentación-de-la-api)
- [Estado del proyecto](#estado-del-proyecto)

## Qué es y para qué sirve

Este backend forma parte de un sistema de control de clima desarrollado como proyecto de Teoría de Control.

El objetivo del sistema es monitorear temperatura y humedad, permitir la configuración de umbrales desde una interfaz web y registrar el historial de mediciones y configuraciones.

El sistema completo utiliza:

* Raspberry Pi 3B+ para ejecutar la adquisición de datos.
* Sensor DHT para medir temperatura y humedad.
* OpenPLC como controlador lógico.
* Relay para accionar el cooler.
* Cooler como actuador de ventilación.
* Spring Boot como API REST.
* MongoDB como base de datos.
* React como frontend de monitoreo y configuración.

## Arquitectura general

```mermaid
flowchart TD
    User[Usuario] --> Web[Frontend React]

    Web -->|POST /api/config| API[Backend Spring Boot]
    Web -->|GET última medición / historial| API

    API --> DB[(MongoDB)]

    DHT[Sensor DHT<br/>Temperatura + Humedad] --> Python[Python Gateway<br/>Raspberry Pi]
    Python <-->|cada intervalo: GET /api/config/latest umbrales| API
    Python -->|cada intervalo: POST /api/measurements temp, humedad, coolerOn| API

    Python -->|Modbus TCP| OpenPLC[OpenPLC Runtime]
    OpenPLC -->|Decisión de control| Python
    Python --> Relay[Relay]
    Relay --> Cooler[Cooler]
```

> **Cadencia de la Raspberry**: en cada `measurementIntervalSeconds` (configurable desde el
> frontend, por defecto 30 s) la Raspberry **(1)** consulta `GET /api/config/latest` para
> obtener los umbrales/histéresis vigentes, **(2)** lee el sensor DHT y decide el estado del
> cooler, y **(3)** publica `POST /api/measurements` con la temperatura, la humedad y si el
> cooler quedó encendido. Así el historial se arma a ese ritmo y los cambios de umbral se
> aplican en el siguiente ciclo.

## Responsabilidad de cada componente

| Componente          | Responsabilidad                                                                       |
| ------------------- | ------------------------------------------------------------------------------------- |
| Frontend React      | Permite configurar umbrales, visualizar estado actual, consultar historial y gráficos |
| Spring Boot Backend | Expone la API REST, valida datos, persiste configuraciones y mediciones               |
| MongoDB             | Guarda historial de configuraciones y mediciones                                      |
| Python Gateway      | Lee el sensor DHT, consulta configuración activa y publica mediciones                 |
| OpenPLC             | Ejecuta la lógica de control usando los valores recibidos                             |
| Relay               | Actúa como interruptor eléctrico para el cooler                                       |
| Cooler              | Actuador físico de ventilación                                                        |
| Sensor DHT          | Fuente de medición de temperatura y humedad                                           |

## Casos de uso

```mermaid
flowchart LR
    Admin([Administrador])
    RPi([Raspberry / Gateway])

    subgraph Sistema
        UC1[Configurar umbrales e intervalo]
        UC2[Ver estado actual]
        UC3[Consultar historial de mediciones]
        UC4[Consultar historial de configuraciones]
        UC5[Publicar medición]
        UC6[Obtener configuración activa]
        UC7[Consultar eventos y alarmas]
        UC8[Reconocer alarma -ACK-]
    end

    Admin --> UC1
    Admin --> UC2
    Admin --> UC3
    Admin --> UC4
    Admin --> UC7
    Admin --> UC8
    RPi --> UC5
    RPi --> UC6
```

| Caso de uso                            | Actor         | Descripción                                                                 | Endpoint                  |
| -------------------------------------- | ------------- | --------------------------------------------------------------------------- | ------------------------- |
| Configurar umbrales e intervalo        | Administrador | Define tempMin/Max, humMin/Max, histéresis e intervalo de medición          | `POST /api/config`        |
| Ver estado actual                      | Administrador | Consulta la última medición y la configuración activa                       | `GET /api/measurements/latest`, `GET /api/config/latest` |
| Consultar historial de mediciones      | Administrador | Lista y filtra mediciones (fecha, estado, rangos, cooler)                   | `GET /api/measurements`   |
| Consultar historial de configuraciones | Administrador | Audita los cambios de umbrales (quién, cuándo, valores)                     | `GET /api/config/history` |
| Obtener configuración activa           | Raspberry     | Lee los umbrales e intervalo vigentes para aplicar el control               | `GET /api/config/latest`  |
| Publicar medición                      | Raspberry     | Envía la lectura del sensor y el estado calculado del cooler                | `POST /api/measurements`  |
| Consultar eventos y alarmas            | Administrador | Lista paginada de eventos derivados (transiciones de estado y del cooler)   | `GET /api/events`         |
| Reconocer alarma (ACK)                 | Administrador | Marca alarmas como reconocidas (una o todas); conteo global sin reconocer   | `POST /api/events/{id}/ack`, `POST /api/events/ack-all` |

### Detalle: configurar umbrales (Administrador)

```text
Actor:          Administrador
Precondición:   El backend y MongoDB están disponibles.
Flujo principal:
  1. El administrador abre la pantalla de Configuración en el frontend.
  2. Carga umbrales, histéresis e intervalo de medición.
  3. El frontend valida (espejo del backend) y envía POST /api/config.
  4. El backend valida, desactiva la config anterior y guarda la nueva como activa.
  5. Queda registrada la auditoría (nombre, email, IP, user-agent, fecha).
Flujos alternativos:
  - Datos inválidos -> 400 con mensajes en español.
  - Demasiadas solicitudes -> 429 (rate limiting).
```

### Detalle: publicar medición (Raspberry)

```text
Actor:          Raspberry / Gateway
Precondición:   Existe una configuración activa.
Flujo principal:
  1. La Raspberry obtiene la config activa (GET /api/config/latest).
  2. Lee el sensor DHT y aplica la lógica de control (histéresis).
  3. Acciona el relay/cooler según el resultado.
  4. Publica la medición (POST /api/measurements) con el estado calculado.
  5. El backend persiste la medición en el historial.
Frecuencia:     cada measurementIntervalSeconds (configurable, por defecto 30 s).
```

## Flujo principal del sistema

```text
1. El usuario ingresa al frontend React.
2. Configura umbrales de temperatura y humedad.
3. React envía la configuración al backend mediante POST /api/config.
4. Spring Boot valida y guarda la configuración activa en MongoDB.
5. Python en la Raspberry consulta la configuración activa con GET /api/config/latest.
6. Python lee temperatura y humedad desde el sensor DHT.
7. Python envía los valores a OpenPLC mediante Modbus TCP.
8. OpenPLC ejecuta la lógica de control.
9. Python obtiene la decisión de OpenPLC y acciona el relay/cooler.
10. Python publica la medición en Spring Boot mediante POST /api/measurements.
11. React consulta dashboard e historial desde el backend.
```

## Qué hace el sistema (diagrama de secuencia)

Este diagrama muestra el ciclo completo: el usuario define los umbrales (y el intervalo de
medición) desde la web, la Raspberry los aplica y, cada cierto intervalo configurable, mide,
decide si prende el cooler y publica la medición; todo queda persistido para auditoría e
historial.

```mermaid
sequenceDiagram
    actor U as Usuario (admin)
    participant FE as Frontend React
    participant API as Backend Spring Boot
    participant DB as MongoDB
    participant RPi as Raspberry (Python + DHT)
    participant HW as Relay + Cooler

    Note over U,DB: 1) Configuración de umbrales (con auditoría)
    U->>FE: Define tempMin/Max, humMin/Max, histéresis e intervalo
    FE->>API: POST /api/config
    API->>DB: Guarda config activa (quién, email, cuándo, IP)
    API-->>FE: 201 Created

    Note over RPi,HW: 2) Bucle de control en la Raspberry
    loop cada measurementIntervalSeconds (ej. 30 s)
        RPi->>API: GET /api/config/latest
        API-->>RPi: Umbrales + histéresis + intervalo
        RPi->>RPi: Lee sensor DHT (temperatura, humedad)
        alt supera umbral (aplicando histéresis)
            RPi->>HW: Enciende cooler (relay ON)
        else dentro de rango
            RPi->>HW: Apaga cooler (relay OFF)
        end
        RPi->>API: POST /api/measurements (temp, hum, coolerOn, status)
        API->>DB: Persiste la medición (historial)
    end

    Note over U,DB: 3) Monitoreo y auditoría
    U->>FE: Abre dashboard / historial
    FE->>API: GET /api/measurements, /api/config/history
    API->>DB: Consulta
    API-->>FE: Datos
    FE-->>U: Estado actual, gráficos e historial de cambios
```

## Rol de OpenPLC

OpenPLC se utiliza como controlador lógico del sistema.

No se conecta directamente a MongoDB. La integración se realiza mediante un gateway en Python que actúa como puente entre:

```text
Sensor DHT / Spring Boot API / OpenPLC / Relay
```

OpenPLC recibe valores como temperatura actual, humedad actual y umbrales configurados. A partir de esos datos, ejecuta la lógica de control y determina si el cooler debe estar encendido o apagado.

## Integración con Modbus TCP

La comunicación entre Python y OpenPLC se realiza mediante Modbus TCP.

Mapa de registros sugerido:

| Registro | Variable        | Descripción                            |
| -------- | --------------- | -------------------------------------- |
| HR0      | TEMP_ACTUAL_X10 | Temperatura actual multiplicada por 10 |
| HR1      | HUM_ACTUAL_X10  | Humedad actual multiplicada por 10     |
| HR2      | TEMP_MIN_X10    | Umbral mínimo de temperatura           |
| HR3      | TEMP_MAX_X10    | Umbral máximo de temperatura           |
| HR4      | HUM_MIN_X10     | Umbral mínimo de humedad               |
| HR5      | HUM_MAX_X10     | Umbral máximo de humedad               |
| HR6      | TEMP_HYST_X10   | Histéresis de temperatura              |
| HR7      | HUM_HYST_X10    | Histéresis de humedad                  |

| Coil | Variable     | Descripción                        |
| ---- | ------------ | ---------------------------------- |
| C0   | COOLER_ON    | Estado calculado del cooler        |
| C1   | SENSOR_ERROR | Indica error de lectura del sensor |

Se utilizan valores multiplicados por 10 para trabajar con enteros en Modbus.

Ejemplo:

```text
25.3 °C → 253
80.9 %  → 809
```

## Lógica de control

La lógica de control utiliza histéresis para evitar que el relay active y desactive el cooler constantemente cerca del umbral.

```mermaid
flowchart TD
    Read[Leer temperatura y humedad] --> CheckHigh{Temp >= TempMax<br/>o Hum >= HumMax?}

    CheckHigh -- Sí --> CoolerOn[Cooler ON]
    CheckHigh -- No --> CheckLow{Temp <= TempMax - HistTemp<br/>y Hum <= HumMax - HistHum?}

    CheckLow -- Sí --> CoolerOff[Cooler OFF]
    CheckLow -- No --> Keep[Mantener estado anterior]

    CoolerOn --> Publish[Publicar medición]
    CoolerOff --> Publish
    Keep --> Publish
```

Regla general:

```text
Si temperatura >= temperatureMax → encender cooler.
Si humedad >= humidityMax → encender cooler.
Si temperatura <= temperatureMax - hysteresisTemperature
y humedad <= humidityMax - hysteresisHumidity → apagar cooler.
```

## Máquina de estados

### Ciclo de control (Raspberry / Gateway)

Estado global del bucle que corre en la Raspberry.

```mermaid
stateDiagram-v2
    [*] --> Inicializando
    Inicializando --> ObteniendoConfig: arranque

    ObteniendoConfig --> LeyendoSensor: config activa obtenida
    ObteniendoConfig --> ErrorConfig: sin conexión / sin config activa

    LeyendoSensor --> Evaluando: lectura válida
    LeyendoSensor --> ErrorSensor: fallo de lectura DHT

    Evaluando --> Enfriando: supera umbral (relay ON)
    Evaluando --> Reposo: dentro de rango (relay OFF)
    Evaluando --> Enfriando: banda muerta y venía enfriando
    Evaluando --> Reposo: banda muerta y venía en reposo

    Enfriando --> Publicando
    Reposo --> Publicando
    ErrorSensor --> Publicando: status CRITICAL / SENSOR_ERROR

    Publicando --> Esperando: POST /api/measurements
    ErrorConfig --> Esperando: reintento

    Esperando --> ObteniendoConfig: pasó measurementIntervalSeconds
```

### Sensor DHT

```mermaid
stateDiagram-v2
    [*] --> LecturaOK
    LecturaOK --> LecturaOK: lectura válida
    LecturaOK --> Error: timeout / CRC inválido
    Error --> LecturaOK: lectura válida
    Error --> Error: sigue fallando (SENSOR_ERROR)
```

### Relay / Cooler

```mermaid
stateDiagram-v2
    [*] --> Apagado
    Apagado --> Encendido: temp >= tempMax o hum >= humMax
    Encendido --> Apagado: temp <= tempMax - histTemp y hum <= humMax - histHum
    Encendido --> Encendido: dentro de la banda muerta (histéresis)
    Apagado --> Apagado: dentro de la banda muerta (histéresis)
```

### OpenPLC

```mermaid
stateDiagram-v2
    [*] --> EsperandoDatos
    EsperandoDatos --> Evaluando: recibe registros Modbus (temp, hum, umbrales)
    Evaluando --> SalidaActualizada: calcula COOLER_ON / SENSOR_ERROR
    SalidaActualizada --> EsperandoDatos: el gateway lee los coils
```

## Modelo de configuración

Se utiliza historial versionado de configuración.

Cada vez que se envía un POST a `/api/config`, se crea un nuevo documento de configuración y se marca como activo. Las configuraciones anteriores quedan desactivadas, pero no se eliminan.

Esto permite auditar:

* quién cambió los umbrales;
* cuándo los cambió;
* desde qué cliente;
* cuáles eran los valores anteriores.

Ejemplo de respuesta de la API (`GET /api/config/latest`):

```json
{
  "id": "665f1c...",
  "temperatureMin": 22.0,
  "temperatureMax": 28.0,
  "humidityMin": 40.0,
  "humidityMax": 90.0,
  "hysteresisTemperature": 1.0,
  "hysteresisHumidity": 2.0,
  "measurementIntervalSeconds": 30,
  "createdByName": "Gabriel Andino",
  "createdByEmail": "gabriel@example.com",
  "active": true,
  "createdAt": "2026-06-03T12:00:00Z"
}
```

> **Datos sensibles**: `clientIp`, `userAgent` y `deviceFingerprint` se usan solo para el
> control anti-abuso y se guardan **únicamente en la base de datos**. No se exponen en la API
> ni se escriben en los logs (los logs de rate limiting registran solo el "bucket", nunca la
> IP/email/fingerprint).

### Intervalo de medición (configurable)

El campo `measurementIntervalSeconds` define **cada cuánto** la Raspberry lee el sensor y
publica una medición. Es parte de la configuración versionada, así que se setea desde el
frontend junto con los umbrales y queda auditado (quién lo cambió y cuándo).

* **Valor por defecto:** 30 segundos.
* **Rango permitido:** 5 a 1800 segundos (media hora), validado en el backend.
* La Raspberry obtiene este valor en `GET /api/config/latest` y lo usa como cadencia de su
  bucle. Al cambiarlo desde la web, la próxima vez que la Raspberry relea la config, ajusta
  el intervalo sin necesidad de redeploy.

> Por qué configurable: un intervalo más corto da un historial más fino pero genera más
> escritura/tráfico; uno más largo es más liviano. 30 s es un buen punto de equilibrio para
> la demo. El mínimo de 5 s evita saturar el backend (y es coherente con el rate limiting).

## Modelo de medición

Cada medición representa una lectura enviada por la Raspberry.

El campo `status` es un enum (`SystemStatus`) con los valores:

* `NORMAL` — temperatura y humedad dentro de rango.
* `WARNING_TEMP` — temperatura fuera de umbral.
* `WARNING_HUMIDITY` — humedad fuera de umbral.
* `CRITICAL` — fuera de umbral más allá de la histéresis.

Ejemplo:

```json
{
  "id": "665f1d...",
  "temperature": 29.1,
  "humidity": 78.2,
  "coolerOn": true,
  "relayOn": true,
  "status": "WARNING_TEMP",
  "createdAt": "2026-06-03T12:05:00Z"
}
```

> **Down-sampling para gráficos**: `GET /api/measurements` acepta `maxPoints`. Si el rango tiene
> más lecturas que ese tope, el backend devuelve una serie **submuestreada repartida en todo el
> rango** (en vez de la página más reciente), así rangos amplios (mes/año) muestran su período
> completo. Sin `maxPoints` se devuelve la página estándar. El panel de "Calidad de control" del
> frontend, en cambio, pide los puntos **sin** `maxPoints` para contar las transiciones reales.

## Eventos y alarmas (derivados)

El sistema no persiste una colección de "eventos": los **deriva del histórico de mediciones** en
el servidor y los **pagina**, así el cliente recibe solo una página por request (no todo el
histórico). Un evento es una **transición**:

* cambio de estado: entrada a `WARNING_TEMP` / `WARNING_HUMIDITY` / `CRITICAL`, o **retorno a
  normal**;
* acción del cooler: **encendido** / **apagado**.

Cada evento tiene un **id estable** (`<id de la medición que lo disparó>-s|-c`), una severidad y
un flag `ackable` (solo las alarmas se reconocen). El **reconocimiento (ACK)** sí se persiste en
la colección `event_acks` (id = id del evento), por lo que es **compartido entre clientes**,
sobrevive reinicios y el conteo de "sin reconocer" es **global** sobre toda la ventana, no solo la
página visible.

| Endpoint | Método | Descripción |
| --- | --- | --- |
| `/api/events` | `GET` | Página de eventos (más nuevo primero), con `acknowledged` por evento. Params: `from`, `to`, `page`, `size`. |
| `/api/events/unacknowledged-count` | `GET` | Conteo **global** de alarmas sin reconocer en la ventana (para el badge). |
| `/api/events/{id}/ack` | `POST` | Reconoce una alarma (idempotente). `204`. |
| `/api/events/ack-all` | `POST` | Reconoce todas las alarmas sin ACK de la ventana. `204`. |

```mermaid
flowchart LR
    M[(measurements)] -->|escaneo ordenado de la ventana| D[Derivar transiciones<br/>estado + cooler]
    D --> P[Paginar en el servidor]
    AK[(event_acks)] -->|join por id de evento| P
    P --> R[EventResponse + acknowledged]
    R --> FE[Frontend: log de eventos + ACK]
```

> Derivar al vuelo mantiene el modelo simple (la verdad es la serie de mediciones); como la
> derivación necesita las lecturas en orden, se escanea la ventana pedida (la ventana acota el
> trabajo). Un paso futuro de escala sería persistir los eventos a medida que ocurren y paginarlos
> directamente desde la base.

## Modelo de datos (UML)

Tres colecciones persistidas en MongoDB (`@Document`) y los DTO/enum derivados. `EventResponse` no
se persiste: se **deriva** de la serie de `Measurement` y se enriquece con el ACK de `EventAck`.

```mermaid
classDiagram
    class Config {
        +String id
        +double temperatureMin
        +double temperatureMax
        +double humidityMin
        +double humidityMax
        +double hysteresisTemperature
        +double hysteresisHumidity
        +int measurementIntervalSeconds
        +String createdByName
        +String createdByEmail
        +String clientIp
        +String userAgent
        +String deviceFingerprint
        +boolean active
        +Instant createdAt
    }
    class Measurement {
        +String id
        +double temperature
        +double humidity
        +boolean coolerOn
        +boolean relayOn
        +SystemStatus status
        +Instant createdAt
    }
    class EventAck {
        +String id
        +Instant ackedAt
    }
    class EventResponse {
        <<DTO derivado>>
        +String id
        +Instant time
        +EventSeverity severity
        +EventType type
        +boolean ackable
        +boolean acknowledged
    }
    class SystemStatus {
        <<enumeration>>
        NORMAL
        WARNING_TEMP
        WARNING_HUMIDITY
        CRITICAL
    }
    class EventType {
        <<enumeration>>
        TEMP_OUT_OF_RANGE
        HUMIDITY_OUT_OF_RANGE
        CRITICAL
        RETURN_TO_NORMAL
        COOLER_ON
        COOLER_OFF
    }
    class EventSeverity {
        <<enumeration>>
        INFO
        SUCCESS
        WARNING
        CRITICAL
    }

    Measurement --> SystemStatus : status
    Measurement ..> EventResponse : deriva transiciones
    EventAck ..> EventResponse : reconoce por id
    EventResponse --> EventType : type
    EventResponse --> EventSeverity : severity
    EventType --> EventSeverity : severidad fija
```

> Colecciones: `configs`, `measurements`, `event_acks`. `Measurement.createdAt` tiene índice TTL
> (retención configurable) que además sirve para los filtros/orden por fecha; `Config.active` y
> `Config.createdByEmail` están indexados para la config activa y los filtros de auditoría.

## Arquitectura por capas (UML)

Arquitectura por capas con **una sola dirección de dependencias** (web → service → repository →
domain). Los controllers no tocan la base; la lógica vive en los services; el acceso a datos está
detrás de los repositorios de Spring Data.

```mermaid
flowchart TD
    subgraph web["web · controllers + DTOs"]
        CC[ConfigController]
        MC[MeasurementController]
        EC[EventController]
    end
    subgraph service["service"]
        CS[ConfigService]
        MS[MeasurementService]
        ES[EventService]
        SS[MeasurementStreamService<br/>SSE]
    end
    subgraph repository["repository · Spring Data Mongo"]
        CR[ConfigRepository]
        MR[MeasurementRepository]
        AR[EventAckRepository]
    end
    subgraph domain["domain · entities + enums"]
        Cf[Config]
        Me[Measurement]
        Ea[EventAck]
    end

    web --> service --> repository --> domain
    repository --> DB[(MongoDB)]
    ES -.->|deriva de| MR
    ES --> AR
```

## Seguridad y anti-abuso

El backend incluye validaciones y límites básicos para evitar abuso de los endpoints públicos.

Protecciones implementadas:

* Rate limiting global por IP.
* Rate limiting específico para `POST /api/config`.
* Rate limiting específico para `POST /api/measurements`.
* Blacklist temporal por IP ante exceso de requests.
* Límite máximo de tamaño de request body.
* Validación estricta de rangos de temperatura y humedad (incluye que la **mínima sea menor
  que la máxima**, no solo el rango absoluto).
* Validación del rango de fechas en los filtros de historial: **"desde" no puede ser posterior
  a "hasta"** y **no se admiten fechas futuras**.
* CORS restringido a los orígenes configurados.

El objetivo no es implementar autenticación completa, sino proteger una API pública simple contra spam o uso abusivo durante la demo del sistema.

## Tiempo real (SSE)

`GET /api/measurements/stream` es un stream **Server-Sent Events**: el backend empuja un evento
`measurement` por cada lectura nueva, así el frontend se actualiza en vivo sin polling. Una
conexión SSE inactiva no ocupa un thread (servlet async); igual está **acotada** para que muchas
pestañas/kioscos abiertos no tumben el servidor:

| Variable de entorno | Default | Qué hace |
| --- | --- | --- |
| `APP_STREAM_ENABLED` | `true` | apaga el stream por completo (el front cae a polling) |
| `APP_STREAM_MAX_SUBSCRIBERS` | `20` | tope **total** de conexiones simultáneas |
| `APP_STREAM_MAX_SUBSCRIBERS_PER_IP` | `3` | tope **por IP** (un cliente con muchas pestañas no acapara) |
| `APP_STREAM_HEARTBEAT_INTERVAL_MS` | `20000` | keep-alive para detectar conexiones caídas |
| `APP_STREAM_TIMEOUT_MS` | `0` | timeout por conexión (`0` = sin límite; ej. `1800000` recicla las viejas) |

Las conexiones que superan un límite se cierran al instante (no se mantienen abiertas), y el
stream también pasa por el rate limiting global, así una tormenta de reconexiones se corta sola.
Los defaults viven en `application.yml` y se pueden sobreescribir por env var (ver
`docker-compose.yml`).

## Ejecutar con Docker

Todo el stack local se puede levantar con:

```bash
docker compose up --build
```

Servicios:

```text
API: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html
Mongo Express: http://localhost:8081
```

## Variables de entorno

Todo es configurable por variable de entorno; los valores por defecto sirven para correr el
proyecto sin configuración previa. Las más usadas:

| Variable | Default | Para qué sirve |
| --- | --- | --- |
| `MONGODB_URI` | `mongodb://localhost:27017/controlsystem` | Conexión a MongoDB. |
| `CORS_ORIGINS` | `http://localhost:5173` | Orígenes permitidos para el frontend (coma-separados). |
| `LOG_LEVEL` | `INFO` | Nivel de log de la app. Poné `DEBUG` para trazas verbosas en local. |
| `APP_RETENTION_MEASUREMENT_DAYS` | `90` | Días que se conservan las mediciones (índice TTL). `0` desactiva el borrado. |
| `APP_CONFIG_API_KEY` | *(vacío)* | Si se setea, `POST /api/config` exige el header `X-Api-Key`. Vacío = sin auth. |
| `MAX_BODY_BYTES` | `8192` | Tamaño máximo del body; por encima responde `413`. |
| `APP_STREAM_ENABLED` | `true` | Habilita el stream SSE de mediciones en tiempo real. |
| `APP_STREAM_HEARTBEAT_INTERVAL_MS` | `20000` | Intervalo del heartbeat que mantiene viva la conexión SSE. |
| `APP_STREAM_MAX_SUBSCRIBERS` | `20` | Tope global de conexiones SSE concurrentes. |
| `APP_STREAM_MAX_SUBSCRIBERS_PER_IP` | `3` | Tope de conexiones SSE por IP (evita que un kiosco acapare cupos). |
| `APP_STREAM_TIMEOUT_MS` | `0` | Timeout por conexión SSE (`0` = sin timeout). |
| `RL_BLACKLIST_MINUTES` | `15` | Minutos que una IP queda bloqueada al superar el umbral de rate limiting. |

El rate limiting por endpoint (`app.rate-limit.*`) se ajusta en
[`application.yml`](src/main/resources/application.yml); los valores por defecto ya están
pensados para el uso real (Raspberry publicando ~1 medición cada 10 s).

## Datos de prueba

El proyecto incluye un seed inicial para MongoDB con:

* configuraciones históricas (con una activa) y nombres acentuados para probar los filtros;
* ~1300 mediciones con resolución mixta: **densas en las últimas 24 h** (una cada 2 min) y más
  espaciadas hasta 14 días atrás (una cada 30 min);
* la **última medición casi "en vivo"** (timestamp ≈ ahora), para que el badge de salud
  muestre *En línea* apenas reseedeás;
* ciclos de cooler con **histéresis real** (estado arrastrado) y los cuatro estados
  (`NORMAL`, `WARNING_TEMP`, `WARNING_HUMIDITY`, `CRITICAL`).

Así se pueden probar todas las vistas: kiosco y rangos cortos del dashboard (1 h/12 h/24 h),
análisis del rango (promedios, % fuera de rango, duty cycle), timeline del cooler, alertas y
los filtros del historial.

Los timestamps son **relativos al momento del seed**, así que para tener datos frescos hay que
regenerar (el seed solo corre con la base vacía):

```bash
docker compose down -v   # borra el volumen y reseedea con datos hasta "ahora"
docker compose up --build
```

Para **reseedear sin borrar el volumen** o **simular la Raspberry en vivo** (badge "en línea",
cooler reaccionando, alerta roja a demanda) cuando el dispositivo todavía no está conectado, ver
[`scripts/README.md`](scripts/README.md).

## Reconstruir / limpiar (Docker)

Si cambiás código del backend, hay que **reconstruir la imagen** (si solo hacés
`docker compose up`, sigue corriendo la imagen vieja):

```bash
docker compose up -d --build           # reconstruye lo que cambió y levanta
```

Reconstrucción forzada ignorando la caché de build:

```bash
docker compose build --no-cache backend
docker compose up -d
```

Resetear los datos de Mongo (borra el volumen y vuelve a ejecutar el seed):

```bash
docker compose down -v                  # ⚠️ borra el volumen mongodb_data
docker compose up -d --build
```

Liberar caché del builder de Docker (global, no solo de este proyecto):

```bash
docker builder prune -f
```

> Recordá: `down -v` borra la data (configuraciones y mediciones) y re-seedea; sin `-v` se
> conserva. El frontend con `npm run dev` toma los cambios solo; el backend en Docker no:
> hay que reconstruir.

## Documentación de la API

La referencia completa de endpoints (rutas, parámetros, filtros, esquemas de request/response
y códigos de estado) está documentada con **OpenAPI/Swagger**, generada desde el código:

```text
Swagger UI:        http://localhost:8080/swagger-ui.html
Especificación:    http://localhost:8080/api-docs
```

Ejemplos rápidos de requests y responses: `docs/examples.http`.

## Estado del proyecto

Este backend forma parte de un sistema mayor compuesto por:

```text
Frontend React
Backend Spring Boot
MongoDB
Python Gateway
OpenPLC Runtime
Raspberry Pi 3B+
Sensor DHT
Relay
Cooler
```

La integración con Raspberry/OpenPLC se realiza desde el Python Gateway, mientras que este backend se encarga de persistir configuración, historial y exponer la API REST para el frontend.

El **frontend** (panel web React que consume esta API) vive en su propio repositorio:
[plc-control-frontend](https://github.com/andinogabriel/plc-control-frontend).
