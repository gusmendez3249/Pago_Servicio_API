package com.proyecto.servicios.exception;

import com.proyecto.servicios.enums.MotivoPago;
import org.springframework.http.HttpStatus;

/**
 * La operación no llegó a procesarse en GestoPago (conexión, credenciales, configuración) y el saldo no se tocó
 * o se reintegró. Se responde 502 si la falla es del proveedor o 500 si es de configuración interna. En el pago,
 * {@code data} trae {@code motivo: PROVEEDOR_NO_DISPONIBLE} (constructor con motivo); la validación de referencia
 * conserva su contrato anterior (sin detalle).
 */
public class ProveedorNoDisponibleException extends OnboardingException {

    public ProveedorNoDisponibleException(String message, HttpStatus status) {
        super(message, status);
    }

    public ProveedorNoDisponibleException(String message, HttpStatus status, MotivoPago motivo) {
        super(message, status, motivo);
    }
}
