package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.DemasiadosIntentosException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperacionesSensiblesRateLimitFilterTest {

    private final AtomicLong reloj = new AtomicLong(1_000_000);
    private final AtomicReference<Exception> resuelta = new AtomicReference<>();
    private final HandlerExceptionResolver resolver = (req, res, h, ex) -> {
        resuelta.set(ex);
        return null;
    };
    private final OperacionesSensiblesRateLimitFilter filtro = new OperacionesSensiblesRateLimitFilter(
            Map.of(OperacionesSensiblesRateLimitFilter.RUTA_PAGO, 2,
                    OperacionesSensiblesRateLimitFilter.RUTA_CLIENTE, 1),
            "CF-Connecting-IP", resolver, reloj::get);

    private boolean pasa(String metodo, String ruta, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.addHeader("CF-Connecting-IP", ip);
        FilterChain cadena = mock(FilterChain.class);
        resuelta.set(null);
        filtro.doFilter(request, new MockHttpServletResponse(), cadena);
        return resuelta.get() == null;
    }

    @Test
    void excedeElLimitePorRutaEIp_Responde429ConSegundos() throws Exception {
        assertTrue(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_PAGO, "1.1.1.1"));
        assertTrue(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_PAGO, "1.1.1.1"));
        assertFalse(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_PAGO, "1.1.1.1"));
        DemasiadosIntentosException ex = assertInstanceOf(DemasiadosIntentosException.class, resuelta.get());
        assertEquals(60, ex.getSegundos());
        assertFalse(ex.getMessage().contains("inicio de sesión"));
    }

    @Test
    void contadoresIndependientesPorIpYPorRuta() throws Exception {
        assertTrue(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_CLIENTE, "1.1.1.1"));
        assertFalse(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_CLIENTE, "1.1.1.1"));
        assertTrue(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_CLIENTE, "2.2.2.2"));
        assertTrue(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_PAGO, "1.1.1.1"));
    }

    @Test
    void laVentanaSeLiberaAlMinuto() throws Exception {
        assertTrue(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_CLIENTE, "1.1.1.1"));
        assertFalse(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_CLIENTE, "1.1.1.1"));
        reloj.addAndGet(60_000);
        assertTrue(pasa("POST", OperacionesSensiblesRateLimitFilter.RUTA_CLIENTE, "1.1.1.1"));
    }

    @Test
    void otrasRutasYMetodosNoSeLimitan() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertTrue(pasa("GET", OperacionesSensiblesRateLimitFilter.RUTA_CLIENTE, "1.1.1.1"));
            assertTrue(pasa("POST", "/api/v1/pagos/historial", "1.1.1.1"));
        }
    }

    private boolean pagaConSesion(OperacionesSensiblesRateLimitFilter f, String ip, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", OperacionesSensiblesRateLimitFilter.RUTA_PAGO);
        request.setRemoteAddr(ip);
        request.addHeader("Authorization", "Bearer " + token);
        resuelta.set(null);
        f.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        return resuelta.get() == null;
    }

    @Test
    void conSesion_ElLimiteEsPorSesionYNoPorIpCompartida() throws Exception {
        OperacionesSensiblesRateLimitFilter f = new OperacionesSensiblesRateLimitFilter(
                Map.of(OperacionesSensiblesRateLimitFilter.RUTA_PAGO, 2), 100, "", resolver,
                com.proyecto.servicios.config.LimitadorTasa.enMemoria(reloj::get));
        assertTrue(pagaConSesion(f, "9.9.9.9", "token-de-ana-aaaaaaaaaaaaaaaaaaaa"));
        assertTrue(pagaConSesion(f, "9.9.9.9", "token-de-ana-aaaaaaaaaaaaaaaaaaaa"));
        assertFalse(pagaConSesion(f, "9.9.9.9", "token-de-ana-aaaaaaaaaaaaaaaaaaaa"));
        assertTrue(pagaConSesion(f, "9.9.9.9", "token-de-luis-bbbbbbbbbbbbbbbbbbb"), "otro usuario del mismo NAT no se bloquea");
    }

    @Test
    void elTopePorIpFrenaAQuienRotaSesiones() throws Exception {
        OperacionesSensiblesRateLimitFilter f = new OperacionesSensiblesRateLimitFilter(
                Map.of(OperacionesSensiblesRateLimitFilter.RUTA_PAGO, 5), 3, "", resolver,
                com.proyecto.servicios.config.LimitadorTasa.enMemoria(reloj::get));
        for (int i = 0; i < 3; i++) {
            assertTrue(pagaConSesion(f, "7.7.7.7", "token-rotado-" + i + "-xxxxxxxxxxxxxxxx"));
        }
        assertFalse(pagaConSesion(f, "7.7.7.7", "token-rotado-9-xxxxxxxxxxxxxxxx"));
    }
}
