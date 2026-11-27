package com.proyecto.servicios.validation;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** idValidacion: obligatorio con referencia, prohibido con teléfono y opcional si el candado está apagado. */
class PagoServicioValidoValidatorIdValidacionTest {

    private static final String ID = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c";

    private final PagoServicioValidoValidator validator = new PagoServicioValidoValidator();

    private static PagoServicioRequest.PagoServicioRequestBuilder base() {
        return PagoServicioRequest.builder().numCuenta("695429041188").idSer(1).idProd(2)
                .monto(new BigDecimal("10.00")).twoFA("1234");
    }

    private boolean valido(PagoServicioRequest request) {
        ConstraintValidatorContext contexto = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);
        return validator.isValid(request, contexto);
    }

    @Test
    void referenciaConIdValidacion_Valida() {
        assertTrue(valido(base().esReferencia(true).reference("REF1").idValidacion(ID).build()));
    }

    @Test
    void referenciaSinIdValidacion_Invalida() {
        assertFalse(valido(base().esReferencia(true).reference("REF1").build()));
    }

    @Test
    void telefonoConIdValidacion_Invalida() {
        assertFalse(valido(base().esReferencia(false).telefono("4181234567").idValidacion(ID).build()));
    }

    @Test
    void telefonoSinIdValidacion_Valida() {
        assertTrue(valido(base().esReferencia(false).telefono("4181234567").build()));
    }

    @Test
    void candadoApagado_IdValidacionDejaDeSerObligatorio() {
        ReflectionTestUtils.setField(validator, "candadoHabilitado", false);

        assertTrue(valido(base().esReferencia(true).reference("REF1").build()));
        assertTrue(valido(base().esReferencia(true).reference("REF1").idValidacion(ID).build()));
        assertFalse(valido(base().esReferencia(false).telefono("4181234567").idValidacion(ID).build()));
    }
}
