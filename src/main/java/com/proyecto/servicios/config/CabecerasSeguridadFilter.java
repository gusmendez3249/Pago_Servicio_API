package com.proyecto.servicios.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cabeceras de seguridad en todas las respuestas (también en los errores que escriben los filtros anteriores):
 * - nosniff: el navegador no reinterpreta el JSON como HTML/script.
 * - DENY: la API no puede incrustarse en un iframe (clickjacking).
 * - HSTS: el navegador solo vuelve a usar HTTPS.
 * - no-store en /api/**: las respuestas con tokens, saldos o datos personales no quedan en cachés intermedias.
 * - CSP restrictiva en /api/**: un JSON abierto directamente en el navegador no puede cargar ni ejecutar nada.
 * - Permissions-Policy: la API no necesita cámara, micrófono, ubicación ni pagos del navegador.
 * - Cross-Origin-Opener/Resource-Policy: aísla la ventana y evita que otros sitios incrusten las respuestas.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // tras CorsConfig (HIGHEST_PRECEDENCE)
public class CabecerasSeguridadFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            // Swagger UI (/swagger-ui.html) sí necesita scripts propios: la CSP estricta solo aplica al API JSON
            response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
            response.setHeader("Cross-Origin-Resource-Policy", "same-site");
        }
        filterChain.doFilter(request, response);
    }
}
