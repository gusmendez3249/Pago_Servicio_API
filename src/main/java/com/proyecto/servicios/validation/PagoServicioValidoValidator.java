package com.proyecto.servicios.validation;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Exige exactamente uno de los dos campos según la bandera. El formato de cada uno lo validan sus @Pattern;
 * aquí cualquier valor presente (incluso vacío) cuenta como "enviado".
 */
public class PagoServicioValidoValidator implements ConstraintValidator<PagoServicioValido, PagoServicioRequest> {

    @Override
    public boolean isValid(PagoServicioRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getEsReferencia() == null) {
            return true; // la bandera ausente ya la reporta @NotNull
        }
        String campo = null;
        String mensaje = null;

        if (!request.getEsReferencia()) {
            if (request.getTelefono() == null) {
                campo = "telefono";
                mensaje = "El teléfono es obligatorio cuando esReferencia es false.";
            } else if (request.getReference() != null) {
                campo = "reference";
                mensaje = "No se debe enviar la referencia cuando esReferencia es false (envíe solo el teléfono).";
            }
        } else {
            if (request.getReference() == null) {
                campo = "reference";
                mensaje = "La referencia es obligatoria cuando esReferencia es true.";
            } else if (request.getTelefono() != null) {
                campo = "telefono";
                mensaje = "No se debe enviar el teléfono cuando esReferencia es true (envíe solo la referencia).";
            }
        }

        if (campo == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
        return false;
    }
}
