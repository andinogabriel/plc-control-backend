package com.control.system.domain.entity;

import com.control.system.domain.enums.SystemStatus;
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

@Document(collection = "measurements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Measurement {

    @Id
    private String id;

    private double temperature;
    private double humidity;
    private boolean coolerOn;
    private boolean relayOn;

    @Indexed
    private SystemStatus status;

    @CreatedDate
    @Indexed
    private Instant createdAt;
}
