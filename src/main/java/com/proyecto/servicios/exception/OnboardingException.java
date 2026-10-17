package com.proyecto.servicios.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de negocio del onboarding. Cada situación tiene su propia subclase
 * (cliente no encontrado, registro duplicado, regla de negocio, etc.) con su estado HTTP;
 * el {@code codigo} de la respuesta coincide con ese estado.
 */
@Getter
public abstract class OnboardingException extends RuntimeException {

    private final HttpStatus status;

    protected OnboardingException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public int getCodigo() {
        return status.value();
    }
}
