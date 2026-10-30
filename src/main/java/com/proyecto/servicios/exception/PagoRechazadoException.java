package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** GestoPago respondió con un CODIGO distinto de "01": el pago no se realizó y el saldo se reintegró. Se responde 422. */
public class PagoRechazadoException extends OnboardingException {

    public PagoRechazadoException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
