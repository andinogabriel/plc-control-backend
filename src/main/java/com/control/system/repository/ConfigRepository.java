package com.control.system.repository;

import com.control.system.domain.entity.Config;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigRepository extends MongoRepository<Config, String>, ConfigRepositoryCustom {

    Optional<Config> findFirstByActiveTrueOrderByCreatedAtDesc();
}
