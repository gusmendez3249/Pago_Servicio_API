package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.DemasiadosIntentosException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Limita los intentos de POST /api/v1/auth/login por IP (ventana fija de 1 minuto) y responde 429.
 *
 * El bloqueo por cuenta de AuthService no frena a quien prueba muchas cuentas desde una misma IP, ni a quien
 * bloquea a propósito la cuenta del ejecutivo con intentos fallidos; este límite cubre ambos casos.
 *
 * La IP del cliente se toma del encabezado {@code app.http.client-ip-header} (CF-Connecting-IP en Render, que
 * Cloudflare siempre sobrescribe) y, si no llega, de la conexión. Fuera de un proxy que fije ese encabezado
 * debe dejarse vacío: de lo contrario el cliente podría inventar su IP.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String RUTA_LOGIN = "/api/v1/auth/login";

    private final int maxIntentos;
    private final String encabezadoIp;
    /** Delega la respuesta de error a GlobalExceptionHandler (el filtro corre fuera de los controladores). */
    private final HandlerExceptionResolver resolver;
    private final LimitadorTasa limitador;

    @Autowired
    public LoginRateLimitFilter(
            @Value("${app.auth.login.max-intentos-por-minuto:30}") int maxIntentos,
            @Value("${app.http.client-ip-header:}") String encabezadoIp,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            LimitadorTasa limitador) {
        this.maxIntentos = maxIntentos;
        this.encabezadoIp = encabezadoIp == null ? "" : encabezadoIp.trim();
        this.resolver = resolver;
        this.limitador = limitador;
    }

    LoginRateLimitFilter(int maxIntentos, String encabezadoIp, HandlerExceptionResolver resolver, LongSupplier reloj) {
        this(maxIntentos, encabezadoIp, resolver, LimitadorTasa.enMemoria(reloj));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && RUTA_LOGIN.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long segundos = limitador.registrar("login|" + ipCliente(request, encabezadoIp), maxIntentos, Duration.ofMinutes(1));
        if (segundos > 0) {
            log.warn("Límite de intentos de login por IP alcanzado; se responde 429");
            resolver.resolveException(request, response, null, new DemasiadosIntentosException(segundos));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** IP del cliente: el encabezado del proxy de confianza si se configuró y llegó; si no, la de la conexión. */
    public static String ipCliente(HttpServletRequest request, String encabezadoIp) {
        if (!encabezadoIp.isEmpty()) {
            String ip = request.getHeader(encabezadoIp);
            if (ip != null && !ip.isBlank()) {
                return ip.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
