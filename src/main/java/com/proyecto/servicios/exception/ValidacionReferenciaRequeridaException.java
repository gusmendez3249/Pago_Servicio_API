package com.proyecto.servicios.exception;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.http.HttpStatus;

/** El pago por referencia exige un comprobante de validación vigente, que corresponda a esa referencia y que no se haya usado. Se responde 428. */
public class ValidacionReferenciaRequeridaException extends OnboardingException {

    public ValidacionReferenciaRequeridaException(String message) {
        super(message, HttpStatus.PRECONDITION_REQUIRED);
    }

    public ValidacionReferenciaRequeridaException(String message, MotivoPago motivo) {
        super(message, HttpStatus.PRECONDITION_REQUIRED, motivo);
    }

    public ValidacionReferenciaRequeridaException(String message, DetalleErrorPago detalle) {
        super(message, HttpStatus.PRECONDITION_REQUIRED, detalle);
    }
}
