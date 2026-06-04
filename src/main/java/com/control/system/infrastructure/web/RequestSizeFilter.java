package com.control.system.infrastructure.web;

import com.control.system.infrastructure.i18n.MessageResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects oversized request bodies (by {@code Content-Length}) with 413 before they reach
 * the controllers. Cheap first line of defense against memory-pressure abuse on a small host.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestSizeFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;
    private final HttpErrorWriter errorWriter;
    private final MessageResolver messages;

    public RequestSizeFilter(
        @Value("${app.request.max-body-bytes:8192}") final long maxBodyBytes,
        final HttpErrorWriter errorWriter,
        final MessageResolver messages
    ) {
        this.maxBodyBytes = maxBodyBytes;
        this.errorWriter = errorWriter;
        this.messages = messages;
    }

    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final @NonNull HttpServletResponse response,
        final @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodyBytes) {
            log.warn("Rejected oversized request: {} bytes", contentLength);
            errorWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
                messages.get("status.payloadTooLarge"), messages.get("error.request.tooLarge", String.valueOf(maxBodyBytes)));
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
