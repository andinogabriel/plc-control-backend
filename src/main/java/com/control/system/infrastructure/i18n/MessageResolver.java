package com.control.system.infrastructure.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around {@link MessageSource} so services and handlers resolve client-facing
 * text from {@code messages.properties} without repeating boilerplate. Uses the request
 * locale (defaults to the bundle without suffix, i.e. Spanish).
 */
@Component
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;

    public String get(final String code, final Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }
}
