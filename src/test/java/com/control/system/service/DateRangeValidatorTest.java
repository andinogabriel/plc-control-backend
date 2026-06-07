package com.control.system.service;

import com.control.system.infrastructure.i18n.MessageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DateRangeValidatorTest {

    @Mock
    private MessageResolver messages;

    @InjectMocks
    private DateRangeValidator validator;

    @Test
    @DisplayName("Given both bounds null, when validate is called, then it does not throw")
    void givenBothBoundsNull_whenValidate_thenDoesNotThrow() {
        assertThatCode(() -> validator.validate(null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given a past 'from' and no 'to', when validate is called, then it does not throw")
    void givenPastFromAndNoTo_whenValidate_thenDoesNotThrow() {
        final Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        assertThatCode(() -> validator.validate(from, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given a past range in order, when validate is called, then it does not throw")
    void givenPastRangeInOrder_whenValidate_thenDoesNotThrow() {
        final Instant to = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant from = to.minus(1, ChronoUnit.DAYS);
        assertThatCode(() -> validator.validate(from, to)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given 'from' after 'to', when validate is called, then it throws IllegalArgumentException")
    void givenFromAfterTo_whenValidate_thenThrows() {
        lenient().when(messages.get("error.dateRange")).thenReturn("rango invalido");
        final Instant to = Instant.now().minus(2, ChronoUnit.DAYS);
        final Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        assertThatThrownBy(() -> validator.validate(from, to))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Given a future 'from', when validate is called, then it throws IllegalArgumentException")
    void givenFutureFrom_whenValidate_thenThrows() {
        lenient().when(messages.get("error.dateFuture")).thenReturn("sin futuro");
        final Instant from = Instant.now().plus(1, ChronoUnit.DAYS);
        assertThatThrownBy(() -> validator.validate(from, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Given a future 'to', when validate is called, then it throws IllegalArgumentException")
    void givenFutureTo_whenValidate_thenThrows() {
        lenient().when(messages.get("error.dateFuture")).thenReturn("sin futuro");
        final Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        final Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
        assertThatThrownBy(() -> validator.validate(from, to))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
