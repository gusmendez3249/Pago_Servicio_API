package com.proyecto.servicios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.GenericResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Rechaza con HTTP 413 las peticiones cuyo cuerpo declarado (Content-Length) excede el límite,
 * antes de que Jackson intente leerlas en memoria. Protege ante payloads masivos enviados por JMeter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(
            @Value("${app.http.max-request-body-bytes:65536}") long maxBytes,
            ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (request.getContentLengthLong() > maxBytes) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), GenericResponse.error(413,
                    "El cuerpo de la petición excede el tamaño máximo permitido (" + maxBytes + " bytes)."));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
