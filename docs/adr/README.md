# Architecture Decision Records (ADR)

Los **ADR** registran las decisiones de arquitectura *significativas* del proyecto: el contexto en
el que se tomaron, la decisión y sus consecuencias. Es la práctica actual (2026) para documentar
arquitectura de forma liviana y versionada junto al código, en vez de un documento monolítico que
queda viejo. Formato: [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

Cada decisión es un archivo numerado e inmutable: si una decisión cambia, se agrega un ADR nuevo que
**supersede** al anterior (no se reescribe la historia).

| ADR | Título | Estado |
| --- | --- | --- |
| [0001](0001-mongodb-document-store.md) | MongoDB como almacén de documentos | Aceptada |
| [0002](0002-derived-events.md) | Eventos/alarmas derivados (no una colección propia) | Aceptada |
| [0003](0003-control-law-in-openplc.md) | La ley de control vive en OpenPLC | Aceptada |
| [0004](0004-managed-deployment.md) | Despliegue gestionado (Atlas + Railway + Cloudflare Pages) | Aceptada |
| [0005](0005-sensor-offline-by-age.md) | Sensor offline inferido por antigüedad del dato | Aceptada |
