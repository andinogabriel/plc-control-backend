package com.control.system.infrastructure.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Wires Bean Validation to the application {@link MessageSource} so constraint messages
 * declared as {@code {message.key}} resolve from {@code messages.properties} (Spanish),
 * instead of the JSR-380 default {@code ValidationMessages.properties} bundle.
 */
@Configuration
public class ValidationConfig {

    @Bean
    public LocalValidatorFactoryBean defaultValidator(final MessageSource messageSource) {
        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
