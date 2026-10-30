package com.proyecto.servicios.validation;

import com.proyecto.servicios.dto.onboarding.SolicitudEstricta;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.stream.Collectors;

public class SinCamposDesconocidosValidator implements ConstraintValidator<SinCamposDesconocidos, SolicitudEstricta> {

    private static final int MAX_LONGITUD_NOMBRE = 40;

    @Override
    public boolean isValid(SolicitudEstricta solicitud, ConstraintValidatorContext context) {
        if (solicitud == null || solicitud.camposDesconocidos().isEmpty()) {
            return true;
        }
        // Los nombres vienen del cliente: se reducen a [A-Za-z0-9_] para que no se interpreten como
        // expresiones en la plantilla del mensaje ({...}, ${...}) ni inyecten texto arbitrario.
        String campos = solicitud.camposDesconocidos().stream()
                .map(SinCamposDesconocidosValidator::sanear)
                .collect(Collectors.joining(", "));
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Campos no permitidos en la solicitud: " + campos + ".")
                .addPropertyNode("camposNoPermitidos")
                .addConstraintViolation();
        return false;
    }

    private static String sanear(String nombre) {
        String limpio = nombre.replaceAll("[^A-Za-z0-9_]", "_");
        return limpio.length() > MAX_LONGITUD_NOMBRE ? limpio.substring(0, MAX_LONGITUD_NOMBRE) : limpio;
    }
}
