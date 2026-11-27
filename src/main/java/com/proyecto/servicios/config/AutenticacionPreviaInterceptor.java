package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.service.onboarding.SesionService;
import com.proyecto.servicios.service.onboarding.SesionUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Autentica (y para las rutas de administrador exige rol EJECUTIVO) ANTES de leer y validar el cuerpo: así quien no
 * tiene sesión recibe 401 y quien no tiene el rol recibe 403, sin conocer los campos ni las reglas de la ruta.
 * Los servicios vuelven a autenticar (defensa en profundidad); las reglas por recurso siguen en ellos.
 */
public class AutenticacionPreviaInterceptor implements HandlerInterceptor {

    /** Rutas que requieren sesión de cualquier rol. */
    static final String[] RUTAS_CON_SESION = {
            "/api/v1/cuentas/**",
            "/api/v1/pagos/servicio",
            "/api/v1/pagos/estado",
            "/api/v1/pagos/historial",
            "/api/v1/pagos/referencia/**",
            "/api/v1/auth/logout"
    };

    /** Rutas que además requieren rol EJECUTIVO. */
    static final String[] RUTAS_DE_EJECUTIVO = {
            "/api/v1/admin/**",
            "/api/v1/pagos/conciliacion",
            "/personas",
            "/personasActualiza",
            "/personasElimina"
    };

    private final SesionService sesionService;
    private final boolean exigirEjecutivo;

    public AutenticacionPreviaInterceptor(SesionService sesionService, boolean exigirEjecutivo) {
        this.sesionService = sesionService;
        this.exigirEjecutivo = exigirEjecutivo;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        SesionUsuario sesion = sesionService.autenticar(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (exigirEjecutivo && !sesion.esEjecutivo()) {
            throw new AccesoDenegadoException("Esta operación es solo para administradores.");
        }
        return true;
    }
}
