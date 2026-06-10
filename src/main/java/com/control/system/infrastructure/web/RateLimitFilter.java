package com.control.system.infrastructure.web;

import com.control.system.infrastructure.i18n.MessageResolver;
import com.control.system.infrastructure.ratelimit.RateLimitException;
import com.control.system.infrastructure.ratelimit.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applies the global per-IP ceiling to every {@code /api} request. This is the main guard
 * against a polling storm or a malicious flood inflating host costs. Per-endpoint limits
 * (config/measurement creation) are enforced deeper, in the services.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final HttpErrorWriter errorWriter;
    private final MessageResolver messages;

    @Override
    protected void doFilterInternal(
        final @NonNull HttpServletRequest request,
        final @NonNull HttpServletResponse response,
        final @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            rateLimitService.checkGlobal(clientIpResolver.resolve(request));
        } catch (final RateLimitException ex) {
            errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                messages.get("status.tooManyRequests"), messages.get(ex.getMessageCode()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        // The SSE stream is one long-lived connection (and auto-reconnects); it must not consume
        // the per-IP request budget.
        return !uri.startsWith("/api/") || uri.startsWith("/api/measurements/stream");
    }
}
