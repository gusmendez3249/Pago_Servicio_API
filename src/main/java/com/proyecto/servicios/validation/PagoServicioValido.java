package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Según la bandera esReferencia exige exactamente uno de los dos campos: telefono (false) o reference (true). */
@Documented
@Constraint(validatedBy = PagoServicioValidoValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PagoServicioValido {

    String message() default "Debe enviar únicamente el teléfono o la referencia según la bandera esReferencia.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
