package com.proyecto.servicios.validation;

import com.proyecto.servicios.dto.onboarding.EstadoPagoRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Cualquier valor presente (incluso vacío) cuenta como "enviado"; el formato lo validan los @Pattern de cada campo. */
public class EstadoPagoValidoValidator implements ConstraintValidator<EstadoPagoValido, EstadoPagoRequest> {

    @Override
    public boolean isValid(EstadoPagoRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        boolean hayUpc = request.getUpc() != null;
        boolean hayLlave = request.getIdempotencyKey() != null;
        if (hayUpc != hayLlave) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(hayUpc
                        ? "Envíe solo uno de los campos: upc o idempotencyKey, no ambos."
                        : "Debe enviar upc o idempotencyKey.")
                .addPropertyNode(hayUpc ? "idempotencyKey" : "upc")
                .addConstraintViolation();
        return false;
    }
}
