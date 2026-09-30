package com.proyecto.servicios.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OnboardingException extends RuntimeException {

    private final HttpStatus status;
    private final int codigo;

    public OnboardingException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
        this.codigo = 400;
    }

    public OnboardingException(String message, HttpStatus status, int codigo) {
        super(message);
        this.status = status;
        this.codigo = codigo;
    }
}
