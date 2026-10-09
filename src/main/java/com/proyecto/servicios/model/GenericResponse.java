package com.proyecto.servicios.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponse<T> {

    private int codigo;
    private String mensaje;
    private T data;

    public static <T> GenericResponse<T> success(T data) {
        return new GenericResponse<>(
                0,
                "Productos obtenidos correctamente",
                data
        );
    }

    public static <T> GenericResponse<T> success(String mensaje, T data) {
        return new GenericResponse<>(0, mensaje, data);
    }

    public static <T> GenericResponse<T> error(
            int codigo,
            String mensaje) {

        return new GenericResponse<>(
                codigo,
                mensaje,
                null
        );
    }

    public static <T> GenericResponse<T> error(
            int codigo,
            String mensaje,
            T data) {

        return new GenericResponse<>(
                codigo,
                mensaje,
                data
        );
    }
}