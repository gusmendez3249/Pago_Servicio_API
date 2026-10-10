package com.proyecto.servicios.exception;

import lombok.Getter;

import java.util.Map;

/** Datos de la petición que incumplen las reglas de validación (campo → mensaje). Se responde 400. */
@Getter
public class ValidacionException extends RuntimeException {

    private final transient Map<String, String> errores;

    public ValidacionException(Map<String, String> errores) {
        super("Datos no validos" + errores);
        this.errores = errores;
    }
}
