package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** Ya existe un cliente con la misma CURP, RFC, correo o nombre de usuario. Se responde 409. */
public class RegistroDuplicadoException extends OnboardingException {

    public RegistroDuplicadoException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
