package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.http.HttpStatus;

/** Recurso inexistente o fuera de los registros a los que el usuario tiene acceso. Se responde 404. */
public class RecursoNoEncontradoException extends OnboardingException {

    public RecursoNoEncontradoException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    public RecursoNoEncontradoException(String message, MotivoPago motivo) {
        super(message, HttpStatus.NOT_FOUND, motivo);
    }

    public RecursoNoEncontradoException(String message, DetalleErrorPago detalle) {
        super(message, HttpStatus.NOT_FOUND, detalle);
    }
}
