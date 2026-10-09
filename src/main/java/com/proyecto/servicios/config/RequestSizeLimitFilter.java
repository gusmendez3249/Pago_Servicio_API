package com.proyecto.servicios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.GenericResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), GenericResponse.error(413, mensaje(maxBytes)));
            return;
        }

        // Sin Content-Length (Transfer-Encoding: chunked) la validación anterior no aplica:
        // se cuenta lo que se va leyendo y se corta al superar el límite.
        if (contentLength < 0) {
            filterChain.doFilter(new LimitedBodyRequest(request, maxBytes), response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    static String mensaje(long maxBytes) {
        return "El cuerpo de la petición excede el tamaño máximo permitido (" + maxBytes + " bytes).";
    }

    /** Se lanza al leer más bytes de los permitidos; GlobalExceptionHandler la traduce a HTTP 413. */
    public static class BodyTooLargeException extends IOException {
        public BodyTooLargeException(long maxBytes) {
            super(mensaje(maxBytes));
        }
    }

    private static class LimitedBodyRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream stream;

        LimitedBodyRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), maxBytes);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String charset = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long leidos;

        LimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                contar(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int n = delegate.read(buffer, off, len);
            if (n > 0) {
                contar(n);
            }
            return n;
        }

        private void contar(int n) throws BodyTooLargeException {
            leidos += n;
            if (leidos > maxBytes) {
                throw new BodyTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
