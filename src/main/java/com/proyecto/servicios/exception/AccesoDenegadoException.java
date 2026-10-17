package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** El usuario está autenticado pero su rol no le permite la operación. Se responde 403. */
public class AccesoDenegadoException extends OnboardingException {

    public AccesoDenegadoException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
