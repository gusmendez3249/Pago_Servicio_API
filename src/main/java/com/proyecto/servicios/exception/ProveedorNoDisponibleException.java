package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

/**
 * La operación no llegó a procesarse en GestoPago (conexión, credenciales, configuración) y el saldo se reintegró.
 * Se responde 502 si la falla es del proveedor o 500 si es de configuración interna.
 */
public class ProveedorNoDisponibleException extends OnboardingException {

    public ProveedorNoDisponibleException(String message, HttpStatus status) {
        super(message, status);
    }
}
