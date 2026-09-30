package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MayorDeEdadValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MayorDeEdad {

    String message() default "El cliente debe ser mayor de edad (mínimo 18 años).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
