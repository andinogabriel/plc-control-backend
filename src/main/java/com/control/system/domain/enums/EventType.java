package com.control.system.domain.enums;

/**
 * Kind of event derived from the measurement series. Each type carries its severity and whether it
 * is an alarm that an operator must acknowledge, so the rest of the code never re-derives that.
 */
public enum EventType {
    TEMP_OUT_OF_RANGE(EventSeverity.WARNING, true),
    HUMIDITY_OUT_OF_RANGE(EventSeverity.WARNING, true),
    CRITICAL(EventSeverity.CRITICAL, true),
    RETURN_TO_NORMAL(EventSeverity.SUCCESS, false),
    COOLER_ON(EventSeverity.INFO, false),
    COOLER_OFF(EventSeverity.INFO, false);

    private final EventSeverity severity;
    private final boolean ackable;

    EventType(final EventSeverity severity, final boolean ackable) {
        this.severity = severity;
        this.ackable = ackable;
    }

    public EventSeverity severity() {
        return severity;
    }

    public boolean ackable() {
        return ackable;
    }
}
