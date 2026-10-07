package com.proyecto.servicios.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.Period;

public class MayorDeEdadValidator implements ConstraintValidator<MayorDeEdad, LocalDate> {

    private static final int EDAD_MINIMA = 18;
    // Evita fechas absurdas como 0001-01-01 o 1500-01-01 que sí serían "mayores de edad"
    private static final int EDAD_MAXIMA = 120;

    @Override
    public boolean isValid(LocalDate fechaNacimiento, ConstraintValidatorContext context) {
        if (fechaNacimiento == null) {
            return false;
        }

        LocalDate hoy = LocalDate.now();
        if (fechaNacimiento.isAfter(hoy)) {
            return false;
        }

        int edad = Period.between(fechaNacimiento, hoy).getYears();
        return edad >= EDAD_MINIMA && edad <= EDAD_MAXIMA;
    }
}

