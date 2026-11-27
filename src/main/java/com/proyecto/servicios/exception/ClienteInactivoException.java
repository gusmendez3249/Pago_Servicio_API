package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.http.HttpStatus;

/** El cliente fue dado de baja (inactivo) y no puede operar ni ser modificado. Se responde 403. */
public class ClienteInactivoException extends OnboardingException {

    public ClienteInactivoException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }

    public ClienteInactivoException(String message, MotivoPago motivo) {
        super(message, HttpStatus.FORBIDDEN, motivo);
    }

    public ClienteInactivoException(String message, DetalleErrorPago detalle) {
        super(message, HttpStatus.FORBIDDEN, detalle);
    }
}
