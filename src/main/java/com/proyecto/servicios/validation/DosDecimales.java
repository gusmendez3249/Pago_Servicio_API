package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = DosDecimalesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DosDecimales {
    String message() default "El ingreso mensual debe incluir obligatoriamente exactamente 2 decimales (ejemplo: 100.00)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
