package com.proyecto.servicios.validation;

import com.proyecto.servicios.dto.onboarding.LoginRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LoginValidoValidator implements ConstraintValidator<LoginValido, LoginRequest> {

    @Override
    public boolean isValid(LoginRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getBandera() == null) {
            return true; // la bandera ausente ya la reporta @NotNull
        }
        String campo = null;
        String mensaje = null;
        if (request.getBandera() == 1 && (request.getPassword() == null || request.getPassword().trim().isEmpty())) {
            campo = "password";
            mensaje = "La contraseña es requerida para autenticación por contraseña (bandera = 1).";
        } else if (request.getBandera() == 2 && request.getFaceIdBiometrico() == null) {
            campo = "faceIdBiometrico";
            mensaje = "El identificador biométrico Face ID es requerido para autenticación por biométrico (bandera = 2).";
        }
        if (campo == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
        return false;
    }
}
