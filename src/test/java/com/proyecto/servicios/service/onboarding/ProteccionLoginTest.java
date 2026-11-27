package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.LimitadorTasa;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ProteccionLoginTest {

    private final AtomicLong reloj = new AtomicLong(1_000_000);
    private final ProteccionLogin proteccion = new ProteccionLogin(LimitadorTasa.enMemoria(reloj::get), "");

    private void desdeIp(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void cincoFallosBloqueanSoloDesdeEsaIp() {
        desdeIp("6.6.6.6");
        for (int i = 0; i < ProteccionLogin.MAX_FALLOS_POR_IP; i++) {
            assertDoesNotThrow(() -> proteccion.verificar("victima"));
            proteccion.registrarFallo("victima");
        }
        assertThrows(CuentaBloqueadaException.class, () -> proteccion.verificar("victima"));
        assertThrows(CuentaBloqueadaException.class, () -> proteccion.verificar("VICTIMA"), "sin distinguir mayúsculas");

        desdeIp("1.2.3.4"); // el titular, desde su red
        assertDoesNotThrow(() -> proteccion.verificar("victima"));
    }

    @Test
    void elBloqueoVenceALos15MinutosYUnLoginCorrectoLoReinicia() {
        desdeIp("6.6.6.6");
        for (int i = 0; i < 5; i++) {
            proteccion.registrarFallo("ana");
        }
        reloj.addAndGet(ProteccionLogin.BLOQUEO.toMillis());
        assertDoesNotThrow(() -> proteccion.verificar("ana"));

        for (int i = 0; i < 4; i++) {
            proteccion.registrarFallo("luis");
        }
        proteccion.limpiar("luis");
        proteccion.registrarFallo("luis");
        assertDoesNotThrow(() -> proteccion.verificar("luis"));
    }
}
