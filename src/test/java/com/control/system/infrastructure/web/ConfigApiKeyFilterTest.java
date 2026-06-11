package com.control.system.infrastructure.web;

import com.control.system.infrastructure.config.SecurityProperties;
import com.control.system.infrastructure.i18n.MessageResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigApiKeyFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;
    @Mock private HttpErrorWriter errorWriter;
    @Mock private MessageResolver messages;

    private ConfigApiKeyFilter filter(final String key) {
        return new ConfigApiKeyFilter(new SecurityProperties(key), errorWriter, messages);
    }

    @Test
    @DisplayName("Given no key configured, when checking, then the filter is skipped")
    void givenDisabled_whenShouldNotFilter_thenSkipped() {
        assertThat(filter("").shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Given a key and POST /api/config, when checking, then the filter applies")
    void givenEnabledConfigPost_whenShouldNotFilter_thenApplies() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/config");
        assertThat(filter("secret").shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("Given a key but a different endpoint, when checking, then the filter is skipped")
    void givenEnabledOtherEndpoint_whenShouldNotFilter_thenSkipped() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/measurements");
        assertThat(filter("secret").shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Given the valid key header, when filtering, then the request proceeds")
    void givenValidKey_whenFilter_thenProceeds() throws Exception {
        when(request.getHeader("X-Api-Key")).thenReturn("secret");
        filter("secret").doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    @Test
    @DisplayName("Given a wrong key, when filtering, then it returns 401 and stops")
    void givenWrongKey_whenFilter_thenUnauthorized() throws Exception {
        when(request.getHeader("X-Api-Key")).thenReturn("nope");
        when(messages.get(anyString())).thenReturn("msg");
        filter("secret").doFilterInternal(request, response, chain);
        verify(errorWriter).write(eq(response), eq(401), anyString(), anyString());
        verify(chain, never()).doFilter(request, response);
    }
}
