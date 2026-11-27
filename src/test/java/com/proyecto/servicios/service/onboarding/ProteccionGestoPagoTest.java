package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ProteccionGestoPagoTest {

    private final AtomicLong reloj = new AtomicLong(0);

    @Test
    void bulkhead_SoloDejaPasarElMaximoDeConcurrentes() {
        ProteccionGestoPago p = new ProteccionGestoPago(2, 5, 30, reloj::get);
        assertTrue(p.entrar());
        assertTrue(p.entrar());
        assertFalse(p.entrar(), "el tercero recibe 503 de inmediato");
        p.salir(ResultadoSendTx.aprobada("01", null));
        assertTrue(p.entrar());
    }

    @Test
    void circuitBreaker_SeAbreTrasFallosSeguidosYSeCierraAlVencer() {
        ProteccionGestoPago p = new ProteccionGestoPago(10, 3, 30, reloj::get);
        for (int i = 0; i < 3; i++) {
            assertTrue(p.entrar());
            p.salir(ResultadoSendTx.noEnviada(ErrorCode.COMMUNICATION_ERROR));
        }
        assertFalse(p.entrar(), "abierto: no se envían pagos");
        reloj.addAndGet(30_000);
        assertTrue(p.entrar(), "tras la pausa pasa uno de prueba");
    }

    @Test
    void unaRespuestaDelProveedorReiniciaLosFallosYLosErroresPreviosNoCuentan() {
        ProteccionGestoPago p = new ProteccionGestoPago(10, 2, 30, reloj::get);
        p.entrar();
        p.salir(ResultadoSendTx.indeterminada(null, null, ErrorCode.TIMEOUT_ERROR));
        p.entrar();
        p.salir(ResultadoSendTx.rechazada("55", "Origen Valido")); // el proveedor sí respondió
        p.entrar();
        p.salir(null); // no se envió (saldo insuficiente): no cuenta
        p.entrar();
        p.salir(ResultadoSendTx.indeterminada(null, null, ErrorCode.TIMEOUT_ERROR));
        assertTrue(p.entrar(), "solo 1 falla seguida: sigue cerrado");
    }
}
