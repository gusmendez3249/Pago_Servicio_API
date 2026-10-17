package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** Los datos tienen un formato válido pero incumplen una regla del sistema (p. ej. intentar cambiar la CURP). Se responde 400. */
public class ReglaNegocioException extends OnboardingException {

    public ReglaNegocioException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
