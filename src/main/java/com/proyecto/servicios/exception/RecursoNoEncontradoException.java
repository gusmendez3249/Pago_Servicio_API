package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** Recurso inexistente o fuera de los registros a los que el usuario tiene acceso. Se responde 404. */
public class RecursoNoEncontradoException extends OnboardingException {

    public RecursoNoEncontradoException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
