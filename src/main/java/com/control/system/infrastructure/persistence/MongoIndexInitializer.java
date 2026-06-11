package com.control.system.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * Creates the query indexes the app relies on. Automatic index creation from the {@code @Indexed}
 * annotations is intentionally NOT enabled: it would try to build a plain {@code {createdAt: 1}}
 * index on {@code measurements}, which collides with the TTL index that
 * {@link MeasurementRetentionInitializer} owns (same key, different name) and fails at startup.
 * So the indexes are declared explicitly here instead.
 *
 * <p>Note: {@code measurements.createdAt} (used by the latest/sort/range queries) is already
 * covered by the TTL index; this class only adds what the TTL index does not.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoIndexInitializer {

    static final String MEASUREMENT_STATUS_INDEX = "measurement_status_createdAt";
    static final String CONFIG_ACTIVE_INDEX = "config_active_createdAt";
    static final String CONFIG_SINGLE_ACTIVE_INDEX = "config_single_active";

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        ensureMeasurementIndexes();
        ensureConfigIndexes();
    }

    private void ensureMeasurementIndexes() {
        // Status-filtered history sorted by date (e.g. "CRITICAL readings, newest first").
        mongoTemplate.indexOps("measurements").ensureIndex(new Index()
            .on("status", Sort.Direction.ASC)
            .on("createdAt", Sort.Direction.DESC)
            .named(MEASUREMENT_STATUS_INDEX));
        log.info("Measurement query indexes ensured");
    }

    private void ensureConfigIndexes() {
        final IndexOperations indexOps = mongoTemplate.indexOps("configs");

        // Serves findFirstByActiveTrueOrderByCreatedAtDesc (the active-config lookup).
        indexOps.ensureIndex(new Index()
            .on("active", Sort.Direction.ASC)
            .on("createdAt", Sort.Direction.DESC)
            .named(CONFIG_ACTIVE_INDEX));

        // Enforce "at most one active config" at the storage layer: the deactivate-then-save flow
        // in ConfigService is not atomic on standalone MongoDB (no multi-document transactions),
        // so a unique partial index is the real guarantee. Best-effort: if the collection already
        // holds more than one active config the index cannot be built, so we log and carry on
        // rather than failing startup.
        try {
            indexOps.ensureIndex(new Index()
                .on("active", Sort.Direction.ASC)
                .named(CONFIG_SINGLE_ACTIVE_INDEX)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("active").is(true))));
            log.info("Config indexes ensured (single-active invariant enforced)");
        } catch (RuntimeException e) {
            log.warn("Could not create the single-active-config index "
                + "(is there more than one active config?): {}", e.getMessage());
        }
    }
}
