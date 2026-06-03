package com.control.system.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Config {

    @Id
    private String id;

    private double temperatureMin;
    private double temperatureMax;
    private double humidityMin;
    private double humidityMax;

    /**
     * Dead-band (degrees Celsius) around the temperature thresholds. Hysteresis prevents the
     * relay/cooler from chattering when the reading hovers at a threshold: it turns on at
     * {@code temperatureMax} and only off once the reading drops below
     * {@code temperatureMax - hysteresisTemperature}.
     */
    private double hysteresisTemperature;

    /**
     * Dead-band (percentage points) around the humidity thresholds, applied with the same
     * anti-chatter logic as {@link #hysteresisTemperature}.
     */
    private double hysteresisHumidity;

    /**
     * How often (in seconds) the Raspberry should read the sensor and publish a measurement.
     * Versioned with the rest of the configuration so the sampling rate is auditable and can
     * be changed from the web UI. Defaults to 30 s.
     */
    private int measurementIntervalSeconds;

    private String createdByName;

    @Indexed
    private String createdByEmail;

    private String clientIp;
    private String userAgent;
    private String deviceFingerprint;

    @Indexed
    private boolean active;

    @CreatedDate
    @Indexed
    private Instant createdAt;
}
