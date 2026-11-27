package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.http.HttpStatus;

/** La llave de idempotencia ya se usó con otros datos, por otro usuario, o la operación ya no admite el cambio. Se responde 409. */
public class OperacionDuplicadaException extends OnboardingException {

    public OperacionDuplicadaException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public OperacionDuplicadaException(String message, MotivoPago motivo) {
        super(message, HttpStatus.CONFLICT, motivo);
    }

    public OperacionDuplicadaException(String message, DetalleErrorPago detalle) {
        super(message, HttpStatus.CONFLICT, detalle);
    }
}
