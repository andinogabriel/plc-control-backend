# 0003 — La ley de control vive en OpenPLC

- **Estado:** Aceptada
- **Fecha:** 2026-06

## Contexto

Es un proyecto de **Teoría de Control**: se busca que la **ley de control** (histéresis / banda
muerta) resida en el **controlador**, no en el backend ni dispersa en un script. Limitante físico:
el **DHT22** usa un protocolo con timing de microsegundos que el modelo de scan-cycle de OpenPLC no
puede leer directamente.

## Decisión

- **OpenPLC** ejecuta la **ley de control** (histéresis → decisión del cooler + `status`) y acciona
  el relay. Es "el controlador".
- El **gateway Python** es un **puente de I/O y de red**: lee el DHT22 (por timing), intercambia
  setpoints/salidas con OpenPLC por **Modbus TCP**, y sincroniza con el backend (GET config / POST
  mediciones). **No decide** nada de control.

## Consecuencias

- El "cerebro" es OpenPLC → defendible académicamente ("el controlador es el PLC").
- El backend queda **pasivo** respecto del control: solo persiste lo que llega y sirve la config.
- La lectura del DHT22 queda en Python por necesidad física; el relay va sobre una salida de OpenPLC.
- Acopla el gateway al mapa de registros Modbus de OpenPLC (documentado en el README).
