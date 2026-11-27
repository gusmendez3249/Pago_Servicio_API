package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import org.springframework.http.HttpStatus;

/**
 * El pago no se realizó (el proveedor lo rechazó) y el saldo se reintegró. Se responde 422 con el
 * {@link PagoServicioResponse} completo (estatus RECHAZADA, motivo y upc) en {@code data}.
 */
public class PagoRechazadoException extends OnboardingException {

    public PagoRechazadoException(String message, PagoServicioResponse respuesta) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, respuesta);
    }
}
