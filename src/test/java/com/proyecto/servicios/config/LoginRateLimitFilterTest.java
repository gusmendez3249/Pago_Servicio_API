package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimitFilterTest {

    private final AtomicLong reloj = new AtomicLong(1_000_000);

    /** Resolver real con GlobalExceptionHandler: el 429 lo arma el manejador central, igual que en la app. */
    private static final HandlerExceptionResolver RESOLVER = resolverConManejadorGlobal();

    private static HandlerExceptionResolver resolverConManejadorGlobal() {
        StaticWebApplicationContext contexto = new StaticWebApplicationContext();
        contexto.registerSingleton("globalExceptionHandler", GlobalExceptionHandler.class);
        contexto.refresh();
        ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver();
        resolver.setApplicationContext(contexto);
        resolver.setMessageConverters(List.of(new MappingJackson2HttpMessageConverter()));
        resolver.afterPropertiesSet();
        return resolver;
    }

    private LoginRateLimitFilter filtro(String encabezadoIp) {
        return new LoginRateLimitFilter(3, encabezadoIp, RESOLVER, reloj::get);
    }

    private static MockHttpServletResponse ejecutar(LoginRateLimitFilter filtro, String metodo, String ruta,
                                                    String ipConexion, String encabezado, String ipEncabezado) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setRemoteAddr(ipConexion);
        if (encabezado != null) {
            request.addHeader(encabezado, ipEncabezado);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletResponse login(LoginRateLimitFilter filtro, String ip) throws Exception {
        return ejecutar(filtro, "POST", LoginRateLimitFilter.RUTA_LOGIN, ip, null, null);
    }

    @Test
    void alSuperarElLimite_Responde429ConRetryAfter() throws Exception {
        LoginRateLimitFilter filtro = filtro("");
        for (int i = 0; i < 3; i++) {
            assertEquals(200, login(filtro, "1.1.1.1").getStatus());
        }
        MockHttpServletResponse bloqueada = login(filtro, "1.1.1.1");
        assertEquals(429, bloqueada.getStatus());
        assertEquals("60", bloqueada.getHeader("Retry-After"));
        assertTrue(bloqueada.getContentAsString().contains("\"codigo\":429"));
    }

    @Test
    void cadaIpTieneSuPropioLimite() throws Exception {
        LoginRateLimitFilter filtro = filtro("");
        for (int i = 0; i < 4; i++) {
            login(filtro, "1.1.1.1");
        }
        assertEquals(200, login(filtro, "2.2.2.2").getStatus());
    }

    @Test
    void laVentanaSeReiniciaAlMinuto() throws Exception {
        LoginRateLimitFilter filtro = filtro("");
        for (int i = 0; i < 4; i++) {
            login(filtro, "1.1.1.1");
        }
        reloj.addAndGet(60_000);
        assertEquals(200, login(filtro, "1.1.1.1").getStatus());
    }

    @Test
    void usaElEncabezadoDelProxyCuandoEstaConfigurado() throws Exception {
        LoginRateLimitFilter filtro = filtro("CF-Connecting-IP");
        // Misma conexión (el proxy) pero clientes distintos: cada uno con su límite
        for (int i = 0; i < 4; i++) {
            ejecutar(filtro, "POST", LoginRateLimitFilter.RUTA_LOGIN, "10.0.0.1", "CF-Connecting-IP", "3.3.3.3");
        }
        assertEquals(429, ejecutar(filtro, "POST", LoginRateLimitFilter.RUTA_LOGIN, "10.0.0.1", "CF-Connecting-IP", "3.3.3.3").getStatus());
        assertEquals(200, ejecutar(filtro, "POST", LoginRateLimitFilter.RUTA_LOGIN, "10.0.0.1", "CF-Connecting-IP", "4.4.4.4").getStatus());
    }

    @Test
    void sinEncabezadoConfigurado_IgnoraElQueEnviaElCliente() throws Exception {
        LoginRateLimitFilter filtro = filtro("");
        for (int i = 0; i < 3; i++) {
            ejecutar(filtro, "POST", LoginRateLimitFilter.RUTA_LOGIN, "5.5.5.5", "CF-Connecting-IP", "falsa-" + i);
        }
        assertEquals(429, ejecutar(filtro, "POST", LoginRateLimitFilter.RUTA_LOGIN, "5.5.5.5", "CF-Connecting-IP", "otra").getStatus());
    }

    @Test
    void otrasRutasYMetodosNoSeLimitan() throws Exception {
        LoginRateLimitFilter filtro = filtro("");
        for (int i = 0; i < 10; i++) {
            assertEquals(200, ejecutar(filtro, "POST", "/api/v1/pagos/servicio", "1.1.1.1", null, null).getStatus());
            assertEquals(200, ejecutar(filtro, "GET", LoginRateLimitFilter.RUTA_LOGIN, "1.1.1.1", null, null).getStatus());
        }
    }
}
