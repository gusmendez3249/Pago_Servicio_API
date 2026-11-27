package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.http.HttpStatus;

/** Acceso bloqueado temporalmente por exceso de intentos fallidos (login o PIN). Se responde 423. */
public class CuentaBloqueadaException extends OnboardingException {

    public CuentaBloqueadaException(String message) {
        super(message, HttpStatus.LOCKED);
    }

    public CuentaBloqueadaException(String message, MotivoPago motivo) {
        super(message, HttpStatus.LOCKED, motivo);
    }

    public CuentaBloqueadaException(String message, DetalleErrorPago detalle) {
        super(message, HttpStatus.LOCKED, detalle);
    }
}
