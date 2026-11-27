package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.LimitadorTasa;
import com.proyecto.servicios.config.LoginRateLimitFilter;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Locale;

/**
 * Bloqueo de login por USUARIO + IP: 5 contraseñas fallidas desde una IP bloquean ese usuario solo desde esa IP
 * durante 15 minutos. Así un tercero ya no puede bloquear la cuenta de otra persona (antes bastaban 5 fallos desde
 * cualquier lugar); el titular sigue entrando desde su red. Contra la fuerza bruta distribuida (muchas IPs) queda el
 * bloqueo global de la cuenta en base de datos, con un umbral mayor (ver AuthService).
 */
@Slf4j
@Component
public class ProteccionLogin {

    public static final int MAX_FALLOS_POR_IP = 5;
    public static final Duration BLOQUEO = Duration.ofMinutes(15);

    private final LimitadorTasa limitador;
    private final String encabezadoIp;

    @Autowired
    public ProteccionLogin(LimitadorTasa limitador, @Value("${app.http.client-ip-header:}") String encabezadoIp) {
        this.limitador = limitador;
        this.encabezadoIp = encabezadoIp == null ? "" : encabezadoIp.trim();
    }

    /** @throws CuentaBloqueadaException 423 si este usuario está bloqueado desde la IP de la petición */
    public void verificar(String username) {
        long segundos = limitador.bloqueo(clave(username), MAX_FALLOS_POR_IP);
        if (segundos > 0) {
            long minutos = Math.max(1, (segundos + 59) / 60);
            throw new CuentaBloqueadaException("Demasiados intentos fallidos desde este dispositivo. Intente de nuevo en "
                    + minutos + " minuto(s).");
        }
    }

    public void registrarFallo(String username) {
        limitador.registrar(clave(username), Integer.MAX_VALUE, BLOQUEO);
    }

    public void limpiar(String username) {
        limitador.limpiar(clave(username));
    }

    private String clave(String username) {
        return "login-fallos|" + username.toLowerCase(Locale.ROOT) + "|" + ipActual();
    }

    private String ipActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest request = atributos.getRequest();
            return LoginRateLimitFilter.ipCliente(request, encabezadoIp);
        }
        return "sin-ip";
    }
}
