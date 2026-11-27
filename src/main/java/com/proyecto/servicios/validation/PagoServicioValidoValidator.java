package com.proyecto.servicios.validation;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

/**
 * Exige exactamente uno de los dos campos según la bandera. El formato de cada uno lo validan sus @Pattern;
 * aquí cualquier valor presente (incluso vacío) cuenta como "enviado".
 */
public class PagoServicioValidoValidator implements ConstraintValidator<PagoServicioValido, PagoServicioRequest> {

    /**
     * Candado de referencia (pagos.referencia.candado.habilitado). Spring inyecta el valor porque instancia los
     * validadores con su propia fábrica; fuera de Spring (pruebas) queda en true, el valor seguro.
     * Con el candado apagado idValidacion deja de ser obligatorio (si viene, se valida su formato y se ignora).
     */
    @Value("${pagos.referencia.candado.habilitado:true}")
    private boolean candadoHabilitado = true;

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
            } else if (request.getIdValidacion() != null) {
                campo = "idValidacion";
                mensaje = "No se debe enviar idValidacion cuando esReferencia es false.";
            }
        } else {
            if (request.getReference() == null) {
                campo = "reference";
                mensaje = "La referencia es obligatoria cuando esReferencia es true.";
            } else if (request.getTelefono() != null) {
                campo = "telefono";
                mensaje = "No se debe enviar el teléfono cuando esReferencia es true (envíe solo la referencia).";
            } else if (request.getIdValidacion() == null && candadoHabilitado) {
                campo = "idValidacion";
                mensaje = "El comprobante de validación (idValidacion) es obligatorio cuando esReferencia es true.";
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
