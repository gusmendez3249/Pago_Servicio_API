package com.proyecto.servicios.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Se superó el límite de intentos de login por IP. Se responde 429 con Retry-After. */
@Getter
public class DemasiadosIntentosException extends OnboardingException {

    private final long segundos;

    public DemasiadosIntentosException(long segundos) {
        super("Demasiados intentos de inicio de sesión. Intente de nuevo en " + segundos + " segundo(s).",
                HttpStatus.TOO_MANY_REQUESTS);
        this.segundos = segundos;
    }

    /** Límite de peticiones de una operación distinta del login (registro, pagos, validación de referencias). */
    public DemasiadosIntentosException(String mensaje, long segundos) {
        super(mensaje, HttpStatus.TOO_MANY_REQUESTS);
        this.segundos = segundos;
    }
}
