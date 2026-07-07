(*
 * control_histeresis.st  -  Controlador on-off del cooler (Camino 1)
 *
 * Toda la LEY DE CONTROL vive en el PLC (IEC 61131-3). El gateway Python es
 * solo I/O de campo: lee el DHT22 y escribe los valores por Modbus, lee la
 * bobina de salida y acciona el rele.
 *
 * Contrato Modbus (OpenPLC = esclavo TCP en :502, gateway = maestro):
 *   ENTRADAS (el gateway ESCRIBE holding registers, mapeados a %QW):
 *     %QW0  Temp     temperatura medida    x10  (23.5 C -> 235, signed)  HR 0
 *     %QW1  Hum      humedad relativa      x10                           HR 1
 *     %QW2  TempMin  limite inferior banda x10  (apaga el cooler)        HR 2
 *     %QW3  TempMax  limite superior banda x10  (enciende el cooler)     HR 3
 *     %QW4  HumMin   solo monitoreo        x10                           HR 4
 *     %QW5  HumMax   solo monitoreo        x10                           HR 5
 *   SALIDAS:
 *     %QX0.0 Cooler  comando al rele  (el gateway lo lee como coil 0)    COIL 0
 *     %QW10  Estado  palabra de estado del controlador (telemetria)      HR 10
 *
 * Ley de control: ON-OFF con BANDA MUERTA (histeresis) sobre [TempMin, TempMax].
 * El cooler enciende al llegar a TempMax y no apaga hasta bajar a TempMin; la
 * banda absorbe el ruido de medicion y evita el chattering. ANTI-CICLADO: se
 * exige >= 15s encendido y >= 15s apagado para proteger el rele. Fail-safe: si
 * TempMax <= TempMin (config invalida o sin escribir) el cooler queda apagado.
 *
 * Estado (telemetria): 0=reposo  1=enfriando  2=espera anti-ciclado  3=fail-safe
 *)
PROGRAM main
  VAR
    Temp     AT %QW0   : INT;
    Hum      AT %QW1   : INT;
    TempMin  AT %QW2   : INT;
    TempMax  AT %QW3   : INT;
    HumMin   AT %QW4   : INT;
    HumMax   AT %QW5   : INT;
    Cooler   AT %QX0.0 : BOOL;
    Estado   AT %QW10  : INT;
  END_VAR
  VAR
    TimerOn  : TON;
    TimerOff : TON;
  END_VAR

  TimerOn(IN := Cooler, PT := T#15s);
  TimerOff(IN := NOT Cooler, PT := T#15s);

  IF (TempMax <= TempMin) THEN
      Cooler := FALSE;
  ELSIF Cooler THEN
      IF (Temp <= TempMin) AND TimerOn.Q THEN
          Cooler := FALSE;
      END_IF;
  ELSE
      IF (Temp >= TempMax) AND TimerOff.Q THEN
          Cooler := TRUE;
      END_IF;
  END_IF;

  IF (TempMax <= TempMin) THEN
      Estado := 3;
  ELSIF Cooler THEN
      IF (Temp <= TempMin) AND NOT TimerOn.Q THEN
          Estado := 2;
      ELSE
          Estado := 1;
      END_IF;
  ELSE
      IF (Temp >= TempMax) AND NOT TimerOff.Q THEN
          Estado := 2;
      ELSE
          Estado := 0;
      END_IF;
  END_IF;

END_PROGRAM


CONFIGURATION Config0

  RESOURCE Res0 ON PLC
    TASK task0(INTERVAL := T#20ms,PRIORITY := 1);
    PROGRAM instance0 WITH task0 : main;
  END_RESOURCE
END_CONFIGURATION
