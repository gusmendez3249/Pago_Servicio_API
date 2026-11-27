package com.proyecto.servicios.service.onboarding;

/**
 * Usuario autenticado en la petición actual.
 *
 * @param clienteId ID interno del cliente dueño de la cuenta (nulo para ejecutivos). Solo se usa dentro del
 *                  servidor para limitar el acceso; nunca se devuelve en una respuesta.
 */
public record SesionUsuario(Long usuarioId, String username, String rol, Long clienteId) {

    public static final String ROL_EJECUTIVO = "EJECUTIVO";
    public static final String ROL_CLIENTE = "CLIENTE";

    public boolean esEjecutivo() {
        return ROL_EJECUTIVO.equals(rol);
    }
}
