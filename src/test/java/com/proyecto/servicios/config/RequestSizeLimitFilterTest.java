package com.proyecto.servicios.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Límite de tamaño del cuerpo: por Content-Length declarado y, sin él (chunked), contando lo leído. */
class RequestSizeLimitFilterTest {

    private static final long LIMITE = 10;

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final RequestSizeLimitFilter filtro = new RequestSizeLimitFilter(LIMITE, resolver);

    private static MockHttpServletRequest conCuerpo(String cuerpo) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api");
        request.setContent(cuerpo.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    /** Simula Transfer-Encoding: chunked, donde el cliente no declara Content-Length. */
    private static MockHttpServletRequest chunked(String cuerpo) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(cuerpo.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    void cuerpoDentroDelLimitePasaSinEnvolver() throws Exception {
        MockHttpServletRequest request = conCuerpo("12345");
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(same(request), any());
        verifyNoInteractions(resolver);
    }

    @Test
    void contentLengthMayorAlLimiteSeRechazaSinLeerElCuerpo() throws Exception {
        MockHttpServletRequest request = conCuerpo("12345678901");
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, new MockHttpServletResponse(), chain);

        verify(resolver).resolveException(same(request), any(), isNull(),
                any(RequestSizeLimitFilter.BodyTooLargeException.class));
        verifyNoInteractions(chain);
    }

    @Test
    void chunkedDentroDelLimiteSeLeeCompleto() throws Exception {
        AtomicReference<String> leido = new AtomicReference<>();
        FilterChain chain = (req, res) -> leido.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        filtro.doFilter(chunked("hola"), new MockHttpServletResponse(), chain);

        assertEquals("hola", leido.get());
    }

    @Test
    void chunkedQueExcedeElLimiteCortaLaLecturaPorBloques() {
        FilterChain chain = (req, res) -> req.getInputStream().readAllBytes();
        MockHttpServletRequest request = chunked("12345678901");
        MockHttpServletResponse response = new MockHttpServletResponse();

        IOException ex = assertThrows(RequestSizeLimitFilter.BodyTooLargeException.class,
                () -> filtro.doFilter(request, response, chain));
        assertEquals(RequestSizeLimitFilter.mensaje(LIMITE), ex.getMessage());
    }

    @Test
    void chunkedQueExcedeElLimiteCortaLaLecturaByteAByte() {
        FilterChain chain = (req, res) -> {
            ServletInputStream in = req.getInputStream();
            while (in.read() != -1) {
                // se consume byte a byte hasta que el filtro corta
            }
        };
        MockHttpServletRequest request = chunked("12345678901");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(RequestSizeLimitFilter.BodyTooLargeException.class,
                () -> filtro.doFilter(request, response, chain));
    }

    @Test
    void readerUsaLaCodificacionDeclaradaOUtf8PorDefecto() throws Exception {
        AtomicReference<String> leido = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            try (BufferedReader reader = req.getReader()) {
                leido.set(reader.readLine());
            }
        };

        MockHttpServletRequest sinCodificacion = chunked("año");
        sinCodificacion.setCharacterEncoding(null);
        filtro.doFilter(sinCodificacion, new MockHttpServletResponse(), chain);
        assertEquals("año", leido.get());

        MockHttpServletRequest latin1 = chunked("abc");
        latin1.setCharacterEncoding("ISO-8859-1");
        filtro.doFilter(latin1, new MockHttpServletResponse(), chain);
        assertEquals("abc", leido.get());
    }

    @Test
    void elStreamEnvueltoDelegaEstadoYListener() throws Exception {
        AtomicReference<ServletRequest> envuelta = new AtomicReference<>();
        filtro.doFilter(chunked("x"), new MockHttpServletResponse(), (req, res) -> envuelta.set(req));

        ServletInputStream stream = envuelta.get().getInputStream();
        assertSame(stream, envuelta.get().getInputStream(), "el stream se crea una sola vez");
        assertFalse(stream.isFinished());
        assertTrue(stream.isReady());
        stream.readAllBytes();
        assertTrue(stream.isFinished());
        ReadListener listener = mock(ReadListener.class);
        assertThrows(UnsupportedOperationException.class, () -> stream.setReadListener(listener),
                "MockHttpServletRequest no soporta lectura asíncrona; se confirma que se delega");
    }
}
