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
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Límite de peticiones POST por IP y por minuto en las operaciones sensibles distintas del login (429 al excederlo):
 * <ul>
 *   <li>Registro y gestión de clientes: frena la creación masiva de cuentas y la enumeración de CURP/RFC/correo.</li>
 *   <li>Pago de servicios y validación de referencias: cada una llama a GestoPago (coste y riesgo de bloqueo del
 *       distribuidor) y la segunda permite sondear referencias de terceros.</li>
 * </ul>
 * Cada ruta lleva su propio contador. Con sesión ({@code Authorization: Bearer}), el límite de la ruta se cuenta por
 * SESIÓN y no por IP: usuarios distintos detrás del mismo NAT (escuela, oficina, datos móviles) no se bloquean entre sí.
 * Además hay un tope por IP más alto ({@code app.http.limite.ip-por-minuto}) que frena a quien rota sesiones.
 * Los contadores viven en Redis ({@link LimitadorTasa}), compartidos entre instancias.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class OperacionesSensiblesRateLimitFilter extends OncePerRequestFilter {

    static final String RUTA_CLIENTE = "/api/v1/layaway/cliente";
    static final String RUTA_PAGO = "/api/v1/pagos/servicio";
    static final String RUTA_VALIDAR_REFERENCIA = "/api/v1/pagos/referencia/validar";
    static final String RUTA_RECARGA = "/api/v1/admin/recargas";

    private static final String MENSAJE = "Se realizaron demasiadas solicitudes en poco tiempo. Intente de nuevo en %d segundo(s).";

    private final Map<String, Integer> limitesPorRuta;
    private final String encabezadoIp;
    private final HandlerExceptionResolver resolver;
    private final int limitePorIp;
    private final LimitadorTasa limitador;

    @Autowired
    public OperacionesSensiblesRateLimitFilter(
            @Value("${app.http.limite.clientes-por-minuto:20}") int clientesPorMinuto,
            @Value("${app.http.limite.pagos-por-minuto:20}") int pagosPorMinuto,
            @Value("${app.http.limite.validaciones-por-minuto:30}") int validacionesPorMinuto,
            @Value("${app.http.limite.ip-por-minuto:300}") int limitePorIp,
            @Value("${app.http.client-ip-header:}") String encabezadoIp,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            LimitadorTasa limitador) {
        this(Map.of(RUTA_CLIENTE, clientesPorMinuto, RUTA_PAGO, pagosPorMinuto,
                RUTA_VALIDAR_REFERENCIA, validacionesPorMinuto, RUTA_RECARGA, pagosPorMinuto),
                limitePorIp, encabezadoIp, resolver, limitador);
    }

    OperacionesSensiblesRateLimitFilter(Map<String, Integer> limitesPorRuta, String encabezadoIp,
                                        HandlerExceptionResolver resolver, LongSupplier reloj) {
        this(limitesPorRuta, Integer.MAX_VALUE, encabezadoIp, resolver, LimitadorTasa.enMemoria(reloj));
    }

    OperacionesSensiblesRateLimitFilter(Map<String, Integer> limitesPorRuta, int limitePorIp, String encabezadoIp,
                                        HandlerExceptionResolver resolver, LimitadorTasa limitador) {
        this.limitesPorRuta = Map.copyOf(limitesPorRuta);
        this.limitePorIp = limitePorIp;
        this.encabezadoIp = encabezadoIp == null ? "" : encabezadoIp.trim();
        this.resolver = resolver;
        this.limitador = limitador;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && limitesPorRuta.containsKey(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ruta = request.getRequestURI();
        String ip = LoginRateLimitFilter.ipCliente(request, encabezadoIp);
        String sesion = huellaSesion(request.getHeader(HttpHeaders.AUTHORIZATION));
        long segundos = limitador.registrar(ruta + "|" + (sesion != null ? "s:" + sesion : "ip:" + ip),
                limitesPorRuta.get(ruta), Duration.ofMinutes(1));
        if (segundos == 0 && limitePorIp != Integer.MAX_VALUE) {
            segundos = limitador.registrar("sensibles|ip:" + ip, limitePorIp, Duration.ofMinutes(1));
        }
        if (segundos > 0) {
            log.warn("Límite de peticiones por IP alcanzado en {}; se responde 429", ruta);
            resolver.resolveException(request, response, null,
                    new DemasiadosIntentosException(String.format(MENSAJE, segundos), segundos));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Huella corta del token de sesión (nunca el token en sí); nula sin encabezado Bearer. */
    static String huellaSesion(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }
        return com.proyecto.servicios.config.onboarding.TokenUtil.hash(token).substring(0, 32);
    }
}
