package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.http.HttpStatus;

/** El usuario está autenticado pero su rol no le permite la operación. Se responde 403. */
public class AccesoDenegadoException extends OnboardingException {

    public AccesoDenegadoException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }

    public AccesoDenegadoException(String message, MotivoPago motivo) {
        super(message, HttpStatus.FORBIDDEN, motivo);
    }

    public AccesoDenegadoException(String message, DetalleErrorPago detalle) {
        super(message, HttpStatus.FORBIDDEN, detalle);
    }
}
