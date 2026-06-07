package com.control.system.service;

import com.control.system.domain.entity.Config;
import com.control.system.infrastructure.i18n.MessageResolver;
import com.control.system.infrastructure.ratelimit.RateLimitService;
import com.control.system.mapping.ConfigMapperImpl;
import com.control.system.repository.ConfigRepository;
import com.control.system.web.dto.request.ConfigRequest;
import com.control.system.web.dto.response.ConfigResponse;
import com.control.system.web.exception.BadRequestException;
import com.control.system.web.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private ConfigRepository configRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private RateLimitService rateLimitService;
    @Spy
    private ConfigMapperImpl configMapper;
    @Mock
    private MessageResolver messages;

    @InjectMocks
    private ConfigService configService;

    @Captor
    private ArgumentCaptor<Config> configCaptor;

    private ConfigRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new ConfigRequest(
            "Gabriel Andino", "Gabriel@Example.com",
            18.0, 26.0, 30.0, 60.0, 1.5, 2.0, 30, "fp-123");
    }

    @Test
    void createConfigDeactivatesPreviousAndSavesNewAsActiveWithAuditMetadata() {
        when(configRepository.save(any(Config.class))).thenAnswer(inv -> inv.getArgument(0));

        final ConfigResponse response = configService.createConfig(validRequest, "1.2.3.4", "JUnit-UA");

        verify(rateLimitService).checkConfigCreation("1.2.3.4", "Gabriel@Example.com", "fp-123");
        verify(mongoTemplate).updateMulti(any(), any(), any(Class.class));
        verify(configRepository).save(configCaptor.capture());

        final Config saved = configCaptor.getValue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getClientIp()).isEqualTo("1.2.3.4");
        assertThat(saved.getUserAgent()).isEqualTo("JUnit-UA");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getTemperatureMin()).isEqualTo(18.0);
        assertThat(response.active()).isTrue();
    }

    @Test
    void createConfigRejectsTemperatureMinNotLessThanMax() {
        when(messages.get("config.temperature.ordering")).thenReturn("config.temperature.ordering");
        final ConfigRequest invalid = new ConfigRequest(
            "X", "x@example.com", 30.0, 20.0, 30.0, 60.0, 1.5, 2.0, 30, null);

        assertThatThrownBy(() -> configService.createConfig(invalid, "ip", "ua"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("config.temperature.ordering");

        verify(configRepository, never()).save(any());
    }

    @Test
    void createConfigRejectsHumidityMinNotLessThanMax() {
        when(messages.get("config.humidity.ordering")).thenReturn("config.humidity.ordering");
        final ConfigRequest invalid = new ConfigRequest(
            "X", "x@example.com", 18.0, 26.0, 70.0, 60.0, 1.5, 2.0, 30, null);

        assertThatThrownBy(() -> configService.createConfig(invalid, "ip", "ua"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("config.humidity.ordering");
    }

    @Test
    void getLatestConfigThrowsWhenNoneActive() {
        when(messages.get("config.notFound")).thenReturn("config.notFound");
        when(configRepository.findFirstByActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configService.getLatestConfig())
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
