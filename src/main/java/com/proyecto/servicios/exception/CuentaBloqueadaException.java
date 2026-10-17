package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** Acceso bloqueado temporalmente por exceso de intentos fallidos de login. Se responde 423. */
public class CuentaBloqueadaException extends OnboardingException {

    public CuentaBloqueadaException(String message) {
        super(message, HttpStatus.LOCKED);
    }
}
