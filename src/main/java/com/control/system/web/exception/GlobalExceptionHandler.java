package com.control.system.web.exception;

import com.control.system.infrastructure.i18n.MessageResolver;
import com.control.system.infrastructure.ratelimit.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final MessageResolver messages;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(final MethodArgumentNotValidException ex) {
        final List<String> details = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .toList();
        log.debug("Validation failed: {}", details);
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            400, messages.get("status.badRequest"), messages.get("error.validation"), details));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(final MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            400, messages.get("status.badRequest"), messages.get("error.typeMismatch", ex.getName())));
    }

    /**
     * Single handler for every deliberate application error. The status and title come from the
     * exception itself ({@link BadRequestException}, {@link ResourceNotFoundException}, ...), so
     * adding a new mapped error is just a new {@link ApiException} subclass - no change here.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(final ApiException ex) {
        log.debug("{} -> {}", ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ErrorResponse.of(
            ex.getStatus().value(), messages.get(ex.getTitleKey()), ex.getMessage()));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(final RateLimitException ex) {
        log.warn("Rate limit triggered (blacklisted={})", ex.isBlacklisted());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ErrorResponse.of(
            429, messages.get("status.tooManyRequests"), messages.get(ex.getMessageCode())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(final Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(
            500, messages.get("status.internalServerError"), messages.get("error.internal")));
    }
}
