package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/** Falta el token, es inválido o la sesión expiró; o las credenciales del login son incorrectas. Se responde 401. */
public class NoAutenticadoException extends OnboardingException {

    public NoAutenticadoException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
