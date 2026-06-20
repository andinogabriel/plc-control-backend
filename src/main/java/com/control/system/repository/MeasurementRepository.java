package com.control.system.repository;

import com.control.system.domain.entity.Measurement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeasurementRepository extends MongoRepository<Measurement, String>, MeasurementRepositoryCustom {

    Optional<Measurement> findFirstByOrderByCreatedAtDesc();

    /** Measurements within [from, to] in chronological order — the input for event derivation. */
    List<Measurement> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);
}
