package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** La llave de idempotencia ya se usó con otros datos, por otro usuario, o la operación ya no admite el cambio. Se responde 409. */
public class OperacionDuplicadaException extends OnboardingException {

    public OperacionDuplicadaException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
