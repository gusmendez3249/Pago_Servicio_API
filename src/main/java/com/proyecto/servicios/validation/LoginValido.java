package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Según la bandera del login exige la contraseña (1) o el Face ID (2). */
@Documented
@Constraint(validatedBy = LoginValidoValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginValido {

    String message() default "Faltan las credenciales correspondientes a la bandera de autenticación.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
