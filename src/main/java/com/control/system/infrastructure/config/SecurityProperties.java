package com.control.system.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Minimal security tunables (prefix {@code app.security}).
 *
 * @param configApiKey when non-blank, {@code POST /api/config} requires a matching
 *                     {@code X-Api-Key} header (returns 401 otherwise). Blank (the default)
 *                     disables the check so the demo works without setup.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    @DefaultValue("") String configApiKey
) {
    public boolean configApiKeyEnabled() {
        return configApiKey != null && !configApiKey.isBlank();
    }
}
