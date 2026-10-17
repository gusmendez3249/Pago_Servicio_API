package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Reglas del cuerpo de {@code POST /api/v1/layaway/cliente} que dependen de la bandera de operación
 * (secciones obligatorias en el registro, llaves completas para actualizar o dar de baja, solo filtros en la
 * consulta, clienteId no permitido). Cada incumplimiento se reporta en el campo que lo provoca.
 */
@Documented
@Constraint(validatedBy = OperacionClienteValidaValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperacionClienteValida {

    String message() default "La petición no cumple las reglas de la operación indicada en la bandera.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
