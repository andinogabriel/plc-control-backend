package com.control.system.service;

import com.control.system.infrastructure.i18n.MessageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Shared validation for date-range search filters. Centralises the two rules every history
 * search must enforce so they are not duplicated per service:
 * <ul>
 *   <li>{@code from} must not be after {@code to}.</li>
 *   <li>Neither bound may be in the future (historical data only). A small tolerance absorbs
 *       clock skew between the client picker and the server clock.</li>
 * </ul>
 * Both bounds are optional; a {@code null} bound is simply skipped (e.g. a {@code from} with no
 * {@code to} means "from that instant onward, up to now").
 */
@Component
@RequiredArgsConstructor
public class DateRangeValidator {

    /** Allowance for client/server clock skew when checking the "no future dates" rule. */
    private static final long FUTURE_TOLERANCE_SECONDS = 60L;

    private final MessageResolver messages;

    public void validate(final Instant from, final Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(messages.get("error.dateRange"));
        }
        final Instant limit = Instant.now().plusSeconds(FUTURE_TOLERANCE_SECONDS);
        if ((from != null && from.isAfter(limit)) || (to != null && to.isAfter(limit))) {
            throw new IllegalArgumentException(messages.get("error.dateFuture"));
        }
    }
}
