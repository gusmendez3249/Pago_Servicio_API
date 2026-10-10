package com.proyecto.servicios.dto.onboarding;

/**
 * Grupo de validación de la bandera de operación. Solo la bandera se valida al recibir la petición;
 * el resto del cuerpo se valida DESPUÉS de autenticar al usuario (banderas 2, 3 y 4), para que quien no
 * presente un token válido reciba 401 y no el detalle de las reglas de validación.
 */
public interface GrupoBandera {
}
