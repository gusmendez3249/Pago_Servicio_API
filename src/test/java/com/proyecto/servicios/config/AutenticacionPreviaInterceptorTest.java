package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.NoAutenticadoException;
import com.proyecto.servicios.service.onboarding.SesionService;
import com.proyecto.servicios.service.onboarding.SesionUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** La sesión y el rol se revisan antes de leer el cuerpo. */
class AutenticacionPreviaInterceptorTest {

    private final SesionService sesionService = mock(SesionService.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static MockHttpServletRequest peticion(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/recargas");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }

    @Test
    void sinSesionResponde401AntesDelCuerpo() {
        when(sesionService.autenticar(null)).thenThrow(new NoAutenticadoException("sin sesión"));
        AutenticacionPreviaInterceptor interceptor = new AutenticacionPreviaInterceptor(sesionService, true);

        assertThrows(NoAutenticadoException.class, () -> interceptor.preHandle(peticion(null), response, new Object()));
    }

    @Test
    void clienteEnRutaDeEjecutivoResponde403() {
        when(sesionService.autenticar("Bearer c")).thenReturn(new SesionUsuario(1L, "cli", SesionUsuario.ROL_CLIENTE, 9L));
        AutenticacionPreviaInterceptor interceptor = new AutenticacionPreviaInterceptor(sesionService, true);

        assertThrows(AccesoDenegadoException.class, () -> interceptor.preHandle(peticion("Bearer c"), response, new Object()));
    }

    @Test
    void ejecutivoPasa() {
        when(sesionService.autenticar("Bearer e")).thenReturn(new SesionUsuario(2L, "eje", SesionUsuario.ROL_EJECUTIVO, null));

        assertTrue(new AutenticacionPreviaInterceptor(sesionService, true).preHandle(peticion("Bearer e"), response, new Object()));
    }

    @Test
    void rutaConSesionAceptaCualquierRol() {
        when(sesionService.autenticar("Bearer c")).thenReturn(new SesionUsuario(1L, "cli", SesionUsuario.ROL_CLIENTE, 9L));

        assertTrue(new AutenticacionPreviaInterceptor(sesionService, false).preHandle(peticion("Bearer c"), response, new Object()));
    }

    @Test
    void preflightCorsNoExigeSesion() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/admin/recargas");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:4200");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");

        assertTrue(new AutenticacionPreviaInterceptor(sesionService, true).preHandle(request, response, new Object()));
        verifyNoInteractions(sesionService);
    }
}
