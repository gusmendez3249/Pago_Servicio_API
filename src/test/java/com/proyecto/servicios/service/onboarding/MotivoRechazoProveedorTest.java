package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.enums.MotivoPago;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MotivoRechazoProveedorTest {

    private final MotivoRechazoProveedor motivos = new MotivoRechazoProveedor("05,55", "", "02");

    @Test
    void codigos05Y55_SonErrorDeGestoPago() {
        assertEquals(MotivoPago.ERROR_GESTOPAGO, motivos.deCodigo("05"));
        assertEquals(MotivoPago.ERROR_GESTOPAGO, motivos.deCodigo(" 55 "));
    }

    @Test
    void elErrorDeGestoPagoTienePrioridadSobreReferenciaInvalida() {
        assertEquals(MotivoPago.ERROR_GESTOPAGO, new MotivoRechazoProveedor("05", "05", "02").deCodigo("05"));
    }

    @Test
    void otrosCodigosConservanSuMotivo() {
        assertEquals(MotivoPago.SERVICIO_NO_DISPONIBLE, motivos.deCodigo("02"));
        assertEquals(MotivoPago.RECHAZADO_PROVEEDOR, motivos.deCodigo("70"));
        assertEquals(MotivoPago.RECHAZADO_PROVEEDOR, motivos.deCodigo(null));
    }
}
