package com.proyecto.servicios.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CORS: lista exacta de orígenes, preflight sin token y encabezados permitidos/expuestos. */
class CorsConfigTest {

    private static final String PERMITIDO = "http://localhost:4200";

    private FilterRegistrationBean<CorsFilter> registro;

    @BeforeEach
    void setUp() {
        registro = new CorsConfig().corsFilterRegistration(" http://localhost:4200 , https://app.ejemplo.com ");
    }

    private MockHttpServletResponse ejecutar(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        registro.getFilter().doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest peticion(String metodo, String origen) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/api/v1/auth/login");
        request.addHeader("Origin", origen);
        request.setServerName("api.local");
        return request;
    }

    @Test
    void origenPermitido_RecibeAccessControlAllowOrigin() throws Exception {
        MockHttpServletResponse response = ejecutar(peticion("POST", PERMITIDO));

        assertEquals(PERMITIDO, response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void segundoOrigenDeLaLista_SeRecortaYSePermite() throws Exception {
        MockHttpServletResponse response = ejecutar(peticion("GET", "https://app.ejemplo.com"));
        assertEquals("https://app.ejemplo.com", response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void origenAjeno_NoRecibeAccessControlAllowOrigin() throws Exception {
        MockHttpServletResponse response = ejecutar(peticion("POST", "https://malicioso.com"));

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertEquals(403, response.getStatus());
    }

    @Test
    void preflightConIdempotencyKey_EsPermitidoSinToken() throws Exception {
        MockHttpServletRequest request = peticion("OPTIONS", PERMITIDO);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Authorization, Content-Type, Idempotency-Key");

        MockHttpServletResponse response = ejecutar(request);

        assertEquals(200, response.getStatus());
        assertEquals(PERMITIDO, response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("Idempotency-Key"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("POST"));
        assertEquals("600", response.getHeader("Access-Control-Max-Age"));
    }

    @Test
    void preflightConHeaderNoPermitido_SeRechaza() throws Exception {
        MockHttpServletRequest request = peticion("OPTIONS", PERMITIDO);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "X-Otro");

        assertEquals(403, ejecutar(request).getStatus());
    }

    @Test
    void headersExpuestosYPrecedencia() throws Exception {
        MockHttpServletResponse response = ejecutar(peticion("GET", PERMITIDO));

        String expuestos = response.getHeader("Access-Control-Expose-Headers");
        assertTrue(expuestos.contains("Retry-After"));
        assertTrue(expuestos.contains("WWW-Authenticate"));
        assertEquals(Ordered.HIGHEST_PRECEDENCE, registro.getOrder());
    }

    @Test
    void elComodinNuncaSeAcepta() {
        assertEquals(List.of("http://a.com"), CorsConfig.parsearOrigenes("*, http://a.com, "));
    }
}
