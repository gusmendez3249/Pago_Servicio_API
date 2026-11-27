package com.proyecto.servicios.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class CabecerasSeguridadFilterTest {

    private static MockHttpServletResponse ejecutar(String ruta) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CabecerasSeguridadFilter().doFilter(new MockHttpServletRequest("GET", ruta), response, new MockFilterChain());
        return response;
    }

    @Test
    void todasLasRespuestasLlevanCabecerasDeSeguridad() throws Exception {
        MockHttpServletResponse response = ejecutar("/productos");
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertTrue(response.getHeader("Strict-Transport-Security").startsWith("max-age="));
        assertNull(response.getHeader("Cache-Control"));
    }

    @Test
    void lasRespuestasDeLaApiNoSeGuardanEnCache() throws Exception {
        assertEquals("no-store", ejecutar("/api/v1/auth/login").getHeader("Cache-Control"));
    }
}
