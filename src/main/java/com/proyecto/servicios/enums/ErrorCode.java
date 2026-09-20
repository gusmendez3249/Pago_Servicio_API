package com.proyecto.servicios.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(
            0,
            "Operación exitosa"
    ),

    AUTHENTICATION_ERROR(
            1,
            "Error de autenticación"
    ),

    HTTP_ERROR(
            2,
            "Respuesta no exitosa del servicio externo"
    ),

    TIMEOUT_ERROR(
            3,
            "Tiempo de espera agotado"
    ),

    COMMUNICATION_ERROR(
            4,
            "Error de comunicación"
    ),

    INVALID_RESPONSE(
            5,
            "Respuesta inválida"
    ),

    NO_DATA(
            6,
            "No se encontraron productos"
    ),

    CONFIGURATION_ERROR(
            7,
            "Error de configuración"
    ),

    INTERNAL_ERROR(
            8,
            "Error interno"
    );

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}