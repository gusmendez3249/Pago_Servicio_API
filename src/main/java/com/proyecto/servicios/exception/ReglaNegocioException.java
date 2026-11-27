package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.http.HttpStatus;

/** Los datos tienen un formato válido pero incumplen una regla del sistema (p. ej. intentar cambiar la CURP). Se responde 400. */
public class ReglaNegocioException extends OnboardingException {

    public ReglaNegocioException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    public ReglaNegocioException(String message, MotivoPago motivo) {
        super(message, HttpStatus.BAD_REQUEST, motivo);
    }

    public ReglaNegocioException(String message, DetalleErrorPago detalle) {
        super(message, HttpStatus.BAD_REQUEST, detalle);
    }
}
