package com.control.system.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Resolves the originating client IP, honouring {@code X-Forwarded-For} when the app runs
 * behind a reverse proxy (Render, Railway, Nginx). Single responsibility, reused by the
 * rate-limit filter and controllers.
 */
@Component
public class ClientIpResolver {

    public String resolve(final HttpServletRequest request) {
        final String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            return StringUtils.trim(StringUtils.split(forwardedFor, ',')[0]);
        }
        return request.getRemoteAddr();
    }
}
