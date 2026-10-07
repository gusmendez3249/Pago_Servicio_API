package com.proyecto.servicios.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class DosDecimalesValidator implements ConstraintValidator<DosDecimales, BigDecimal> {

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Se evalúa aparte mediante @NotNull
        }
        // Exigir que la cantidad de decimales (scale) sea exactamente 2 (ej. 100.00)
        return value.scale() == 2;
    }
}
