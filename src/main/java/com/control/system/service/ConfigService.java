package com.control.system.service;

import com.control.system.domain.entity.Config;
import com.control.system.infrastructure.i18n.MessageResolver;
import com.control.system.infrastructure.ratelimit.RateLimitService;
import com.control.system.mapping.ConfigMapper;
import com.control.system.repository.ConfigRepository;
import com.control.system.repository.filter.ConfigSearchFilter;
import com.control.system.web.dto.request.ConfigRequest;
import com.control.system.web.dto.response.ConfigResponse;
import com.control.system.web.dto.response.PageResponse;
import com.control.system.web.exception.BadRequestException;
import com.control.system.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final ConfigRepository configRepository;
    private final MongoTemplate mongoTemplate;
    private final RateLimitService rateLimitService;
    private final ConfigMapper configMapper;
    private final MessageResolver messages;
    private final DateRangeValidator dateRangeValidator;

    public ConfigResponse createConfig(final ConfigRequest request, final String clientIp, final String userAgent) {
        rateLimitService.checkConfigCreation(clientIp, request.createdByEmail(), request.deviceFingerprint());
        validateThresholdOrdering(request);

        final Config config = configMapper.toEntity(request);
        config.setClientIp(clientIp);
        config.setUserAgent(userAgent);
        config.setActive(true);
        config.setCreatedAt(Instant.now());

        final Config saved = deactivateAndSave(config);
        log.info("New config created id={}", saved.getId());
        return configMapper.toResponse(saved);
    }

    /**
     * Deactivates any currently active config and saves the new one as active. The two writes are
     * not atomic (standalone MongoDB has no multi-document transactions), so the unique partial
     * index on {@code active} can reject the save if a concurrent request won the race. In that
     * case we deactivate the other winner and retry once.
     */
    private Config deactivateAndSave(final Config config) {
        try {
            deactivateExistingConfigs();
            return configRepository.save(config);
        } catch (final DuplicateKeyException race) {
            log.warn("Concurrent config activation detected; deactivating and retrying once");
            deactivateExistingConfigs();
            return configRepository.save(config);
        }
    }

    public ConfigResponse getLatestConfig() {
        return configRepository.findFirstByActiveTrueOrderByCreatedAtDesc()
            .map(configMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("config.notFound")));
    }

    public PageResponse<ConfigResponse> searchConfigHistory(final ConfigSearchFilter filter, final Pageable pageable) {
        dateRangeValidator.validate(filter.from(), filter.to());
        return PageResponse.from(configRepository.search(filter, pageable).map(configMapper::toResponse));
    }

    private void deactivateExistingConfigs() {
        mongoTemplate.updateMulti(
            Query.query(Criteria.where("active").is(true)),
            Update.update("active", false),
            Config.class
        );
    }

    private void validateThresholdOrdering(final ConfigRequest request) {
        if (request.temperatureMin() >= request.temperatureMax()) {
            throw new BadRequestException(messages.get("config.temperature.ordering"));
        }
        if (request.humidityMin() >= request.humidityMax()) {
            throw new BadRequestException(messages.get("config.humidity.ordering"));
        }
    }
}
