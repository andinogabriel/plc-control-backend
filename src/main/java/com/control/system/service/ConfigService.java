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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final ConfigRepository configRepository;
    private final MongoTemplate mongoTemplate;
    private final RateLimitService rateLimitService;
    private final ConfigMapper configMapper;
    private final MessageResolver messages;

    public ConfigResponse createConfig(final ConfigRequest request, final String clientIp, final String userAgent) {
        rateLimitService.checkConfigCreation(clientIp, request.createdByEmail(), request.deviceFingerprint());
        validateThresholdOrdering(request);

        deactivateExistingConfigs();

        final Config config = configMapper.toEntity(request);
        config.setClientIp(clientIp);
        config.setUserAgent(userAgent);
        config.setActive(true);
        config.setCreatedAt(Instant.now());

        final Config saved = configRepository.save(config);
        log.info("New config created id={}", saved.getId());
        return configMapper.toResponse(saved);
    }

    public ConfigResponse getLatestConfig() {
        return configRepository.findFirstByActiveTrueOrderByCreatedAtDesc()
            .map(configMapper::toResponse)
            .orElseThrow(() -> new NoSuchElementException(messages.get("config.notFound")));
    }

    public PageResponse<ConfigResponse> searchConfigHistory(final ConfigSearchFilter filter, final Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new IllegalArgumentException(messages.get("error.dateRange"));
        }
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
            throw new IllegalArgumentException(messages.get("config.temperature.ordering"));
        }
        if (request.humidityMin() >= request.humidityMax()) {
            throw new IllegalArgumentException(messages.get("config.humidity.ordering"));
        }
    }
}
