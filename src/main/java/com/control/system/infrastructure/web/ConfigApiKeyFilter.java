package com.control.system.infrastructure.web;

import com.control.system.infrastructure.config.SecurityProperties;
import com.control.system.infrastructure.i18n.MessageResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Optional API-key gate for the only write that mutates configuration: {@code POST /api/config}.
 * When {@code app.security.config-api-key} is set, the request must carry a matching
 * {@code X-Api-Key} header; otherwise it is rejected with 401. Disabled (the key is blank) by
 * default so the demo works out of the box.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class ConfigApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Api-Key";

    private final SecurityProperties properties;
    private final HttpErrorWriter errorWriter;
    private final MessageResolver messages;

    @Override
    protected void doFilterInternal(
        final @NonNull HttpServletRequest request,
        final @NonNull HttpServletResponse response,
        final @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.configApiKey().equals(request.getHeader(HEADER))) {
            errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                messages.get("status.unauthorized"), messages.get("error.unauthorized.config"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return !(properties.configApiKeyEnabled()
            && HttpMethod.POST.matches(request.getMethod())
            && "/api/config".equals(request.getRequestURI()));
    }
}
