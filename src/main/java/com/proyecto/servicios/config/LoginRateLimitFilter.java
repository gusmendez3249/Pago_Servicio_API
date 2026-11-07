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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String RUTA_LOGIN = "/api/v1/auth/login";
    private static final long VENTANA_MS = 60_000;
    /** Tope de IPs en memoria: al superarlo se descartan las ventanas vencidas. */
    private static final int MAX_IPS = 10_000;

    private final int maxIntentos;
    private final String encabezadoIp;
    /** Delega la respuesta de error a GlobalExceptionHandler (el filtro corre fuera de los controladores). */
    private final HandlerExceptionResolver resolver;
    private final LongSupplier reloj;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimitFilter(
            @Value("${app.auth.login.max-intentos-por-minuto:10}") int maxIntentos,
            @Value("${app.http.client-ip-header:}") String encabezadoIp,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this(maxIntentos, encabezadoIp, resolver, System::currentTimeMillis);
    }

    LoginRateLimitFilter(int maxIntentos, String encabezadoIp, HandlerExceptionResolver resolver, LongSupplier reloj) {
        this.maxIntentos = maxIntentos;
        this.encabezadoIp = encabezadoIp == null ? "" : encabezadoIp.trim();
        this.resolver = resolver;
        this.reloj = reloj;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && RUTA_LOGIN.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long ahora = reloj.getAsLong();
        if (ventanas.size() > MAX_IPS) {
            ventanas.values().removeIf(v -> v.vencida(ahora));
        }
        Ventana ventana = ventanas.compute(ipCliente(request),
                (ip, v) -> v == null || v.vencida(ahora) ? new Ventana(ahora) : v.contar());

        if (ventana.intentos > maxIntentos) {
            long segundos = Math.max(1, (ventana.inicio + VENTANA_MS - ahora + 999) / 1000);
            log.warn("Límite de intentos de login por IP alcanzado; se responde 429");
            resolver.resolveException(request, response, null, new DemasiadosIntentosException(segundos));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String ipCliente(HttpServletRequest request) {
        if (!encabezadoIp.isEmpty()) {
            String ip = request.getHeader(encabezadoIp);
            if (ip != null && !ip.isBlank()) {
                return ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static final class Ventana {
        private final long inicio;
        private final int intentos;

        Ventana(long inicio) {
            this(inicio, 1);
        }

        private Ventana(long inicio, int intentos) {
            this.inicio = inicio;
            this.intentos = intentos;
        }

        Ventana contar() {
            return new Ventana(inicio, intentos + 1);
        }

        boolean vencida(long ahora) {
            return ahora - inicio >= VENTANA_MS;
        }
    }
}
