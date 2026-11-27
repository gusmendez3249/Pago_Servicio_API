package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Exige exactamente uno de los dos identificadores de la consulta de estado: upc o idempotencyKey. */
@Documented
@Constraint(validatedBy = EstadoPagoValidoValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EstadoPagoValido {

    String message() default "Debe enviar exactamente uno de los campos: upc o idempotencyKey.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
