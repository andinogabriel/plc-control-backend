package com.control.system.infrastructure.web;

import com.control.system.web.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes a JSON {@link ErrorResponse} straight to the servlet response. Used by servlet
 * filters, which run before the {@code @RestControllerAdvice} can intervene.
 */
@Component
@RequiredArgsConstructor
public class HttpErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(final HttpServletResponse response, final int status, final String error, final String message)
        throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(status, error, message));
    }
}
