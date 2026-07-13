(*
 * control_histeresis_ladder.st  -  DERIVADO DE PROGRAMA LADDER (OpenPLC Editor)
 *
 * Generado desde el diagrama Ladder (5 rungs) via xml2st. Ley de control identica
 * a control_histeresis.st: on-off con banda muerta (histeresis) + anti-ciclado
 * (TON 15s) + fail-safe. Unica diferencia: no computa la palabra 'Estado' (%QW10),
 * que es solo telemetria local del gateway (no se envia al backend/frontend).
 *
 * Contrato Modbus (OpenPLC esclavo TCP :502):
 *   %QW0 Temp  %QW1 Hum  %QW2 TempMin  %QW3 TempMax  %QW4 HumMin  %QW5 HumMax  (x10, HR)
 *   %QX0.0 Cooler (coil 0, comando al rele)
 *)

PROGRAM main
  VAR
    Temp AT %QW0 : INT;
    Hum AT %QW1 : INT;
    TempMin AT %QW2 : INT;
    TempMax AT %QW3 : INT;
    HumMin AT %QW4 : INT;
    HumMax AT %QW5 : INT;
    Cooler AT %QX0.0 : BOOL;
  END_VAR
  VAR
    TimerOn : TON;
    TimerOff : TON;
    _TMP_GE304_ENO : BOOL;
    _TMP_GE304_OUT : BOOL;
    _TMP_LE404_ENO : BOOL;
    _TMP_LE404_OUT : BOOL;
    _TMP_LE504_ENO : BOOL;
    _TMP_LE504_OUT : BOOL;
  END_VAR

  TimerOn(IN := Cooler, PT := T#15s);
  TimerOff(IN := NOT(Cooler), PT := T#15s);
  _TMP_GE304_OUT := GE(EN := TRUE, IN1 := Temp, IN2 := TempMax, ENO => _TMP_GE304_ENO);
  IF TimerOff.Q AND _TMP_GE304_OUT THEN
    Cooler := TRUE; (*set*)
  END_IF;
  _TMP_LE404_OUT := LE(EN := TRUE, IN1 := Temp, IN2 := TempMin, ENO => _TMP_LE404_ENO);
  IF TimerOn.Q AND _TMP_LE404_OUT THEN
    Cooler := FALSE; (*reset*)
  END_IF;
  _TMP_LE504_OUT := LE(EN := TRUE, IN1 := TempMax, IN2 := TempMin, ENO => _TMP_LE504_ENO);
  IF _TMP_LE504_OUT THEN
    Cooler := FALSE; (*reset*)
  END_IF;
END_PROGRAM


CONFIGURATION Config0

  RESOURCE Res0 ON PLC
    TASK task0(INTERVAL := T#20ms,PRIORITY := 1);
    PROGRAM instance0 WITH task0 : main;
  END_RESOURCE
END_CONFIGURATION
