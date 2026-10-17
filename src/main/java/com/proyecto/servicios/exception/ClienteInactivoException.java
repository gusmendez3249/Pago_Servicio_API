package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** El cliente fue dado de baja (inactivo) y no puede operar ni ser modificado. Se responde 403. */
public class ClienteInactivoException extends OnboardingException {

    public ClienteInactivoException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
