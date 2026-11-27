package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.enums.MotivoPago;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de negocio del onboarding. Cada situación tiene su propia subclase
 * (cliente no encontrado, registro duplicado, regla de negocio, etc.) con su estado HTTP;
 * el {@code codigo} de la respuesta coincide con ese estado. Opcionalmente trae un {@code detalle}
 * (p. ej. el motivo enumerado de un error de pago) que el manejador devuelve en {@code data}.
 */
@Getter
public abstract class OnboardingException extends RuntimeException {

    private final HttpStatus status;
    private final transient Object detalle;

    protected OnboardingException(String message, HttpStatus status) {
        this(message, status, (Object) null);
    }

    protected OnboardingException(String message, HttpStatus status, Object detalle) {
        super(message);
        this.status = status;
        this.detalle = detalle;
    }

    protected OnboardingException(String message, HttpStatus status, MotivoPago motivo) {
        this(message, status, (Object) DetalleErrorPago.de(motivo));
    }

    public int getCodigo() {
        return status.value();
    }
}
