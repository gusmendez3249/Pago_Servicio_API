package com.proyecto.servicios.exception;

import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.http.HttpStatus;

/** La recarga rebasaría el tope diario de la cuenta o del ejecutivo. Se responde 422 y no se abona nada. */
public class TopeRecargaException extends OnboardingException {

    private final MotivoPago motivo;

    public TopeRecargaException(String message, MotivoPago motivo) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, motivo);
        this.motivo = motivo;
    }

    public MotivoPago getMotivo() {
        return motivo;
    }
}
