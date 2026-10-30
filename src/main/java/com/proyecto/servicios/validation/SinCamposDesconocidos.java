package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rechaza la solicitud si el JSON trae campos que el DTO no define. */
@Documented
@Constraint(validatedBy = SinCamposDesconocidosValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SinCamposDesconocidos {

    String message() default "La solicitud contiene campos no permitidos.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
