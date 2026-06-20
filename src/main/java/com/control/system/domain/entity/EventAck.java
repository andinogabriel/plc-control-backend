package com.control.system.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Operator acknowledgement of an alarm event. The document id is the stable event id (the
 * triggering measurement id + suffix), so acknowledging is idempotent — re-acking the same event
 * just overwrites {@code ackedAt} instead of creating duplicates.
 */
@Document(collection = "event_acks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventAck {

    @Id
    private String id;

    private Instant ackedAt;
}
