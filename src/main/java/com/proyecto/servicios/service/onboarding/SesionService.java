package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.TokenUtil;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.exception.NoAutenticadoException;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Autenticación de cada petición protegida mediante el encabezado {@code Authorization: Bearer <token>}
 * obtenido en {@code POST /api/v1/auth/login}. La sesión vence tras {@value #INACTIVIDAD_MINUTOS} minutos
 * sin actividad; cada petición autenticada renueva el plazo.
 */
@Service
@RequiredArgsConstructor
public class SesionService {

    public static final int INACTIVIDAD_MINUTOS = 5;

    private static final Pattern TOKEN_VALIDO = Pattern.compile("^[A-Za-z0-9_-]{20,100}$");
    private static final String MENSAJE_SIN_TOKEN =
            "Se requiere autenticación: envíe el encabezado 'Authorization: Bearer <token>' obtenido en POST /api/v1/auth/login.";
    private static final String MENSAJE_TOKEN_INVALIDO =
            "Token inválido o sesión expirada. Inicie sesión de nuevo en POST /api/v1/auth/login.";

    private final UsuarioLoginRepository usuarioLoginRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * @return el usuario dueño del token
     * @throws NoAutenticadoException   401 si falta, está mal formado, no existe o expiró
     * @throws ClienteInactivoException 403 si el cliente asociado fue dado de baja
     */
    public SesionUsuario autenticar(String authorization) {
        String token = extraerToken(authorization);
        String hash = TokenUtil.hash(token);

        // La transacción devuelve null cuando hay que responder 401: lanzar dentro revertiría la limpieza del token vencido
        SesionUsuario sesion = transactionTemplate.execute(status -> {
            UsuarioLoginEntity usuario = usuarioLoginRepository.findByTokenHash(hash).orElse(null);
            if (usuario == null) {
                return null;
            }
            LocalDateTime ahora = LocalDateTime.now();
            if (!Boolean.TRUE.equals(usuario.getIsLoggedIn()) || usuario.getTokenExpira() == null
                    || !usuario.getTokenExpira().isAfter(ahora)) {
                usuario.setTokenHash(null);
                usuario.setTokenExpira(null);
                usuario.setIsLoggedIn(false);
                usuarioLoginRepository.save(usuario);
                return null;
            }

            ClienteEntity cliente = usuario.getCliente();
            if (cliente != null && Boolean.FALSE.equals(cliente.getActivo())) {
                throw new ClienteInactivoException("El cliente asociado a este usuario se encuentra inactivo.");
            }

            // Renovación de la sesión por actividad
            usuario.setUltimaActividad(ahora);
            usuario.setTokenExpira(ahora.plusMinutes(INACTIVIDAD_MINUTOS));
            usuarioLoginRepository.save(usuario);

            return new SesionUsuario(usuario.getId(), usuario.getUsername(), usuario.getRol(),
                    cliente != null ? cliente.getId() : null);
        });

        if (sesion == null) {
            throw new NoAutenticadoException(MENSAJE_TOKEN_INVALIDO);
        }
        return sesion;
    }

    private static String extraerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new NoAutenticadoException(MENSAJE_SIN_TOKEN);
        }
        String valor = authorization.trim();
        if (!valor.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            throw new NoAutenticadoException("Esquema de autenticación no soportado: use 'Authorization: Bearer <token>'.");
        }
        String token = valor.substring(7).trim();
        if (!TOKEN_VALIDO.matcher(token).matches()) {
            throw new NoAutenticadoException(MENSAJE_TOKEN_INVALIDO);
        }
        return token;
    }
}
