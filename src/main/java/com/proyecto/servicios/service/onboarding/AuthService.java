package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.dto.onboarding.LoginRequest;
import com.proyecto.servicios.dto.onboarding.LoginResponse;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.exception.OnboardingException;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioLoginRepository usuarioLoginRepository;
    private final PasswordEncoderUtil passwordEncoderUtil;
    private final TransactionTemplate transactionTemplate;

    private static final int INACTIVIDAD_MINUTOS_MAX = 5;
    private static final short MAX_INTENTOS_FALLIDOS = 5;
    private static final int BLOQUEO_MINUTOS = 15;
    // Mismo mensaje para usuario inexistente y credencial incorrecta: no revela qué usuarios existen
    private static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas.";

    public LoginResponse login(LoginRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new OnboardingException("El nombre de usuario es obligatorio.", HttpStatus.BAD_REQUEST, 400);
        }

        // Validaciones de forma antes de consultar la BD
        Integer bandera = request.getBandera();
        if (bandera == null) {
            throw new OnboardingException("La bandera de autenticación es obligatoria (1 = Contraseña, 2 = Biométrico Face ID).", HttpStatus.BAD_REQUEST, 400);
        }
        if (bandera == 1) {
            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                throw new OnboardingException("La contraseña es requerida para autenticación por contraseña.", HttpStatus.BAD_REQUEST, 400);
            }
        } else if (bandera == 2) {
            if (request.getFaceIdBiometrico() == null) {
                throw new OnboardingException("El identificador biométrico Face ID es requerido para autenticación por biométrico.", HttpStatus.BAD_REQUEST, 400);
            }
        } else {
            throw new OnboardingException("Bandera de autenticación no válida: " + bandera + ". Use 1 (Contraseña) o 2 (Biométrico).", HttpStatus.BAD_REQUEST, 400);
        }

        LocalDateTime ahora = LocalDateTime.now();

        // Fase 1 (transacción corta): leer usuario, verificar bloqueo y expirar sesión inactiva
        UsuarioLoginEntity usuario = transactionTemplate.execute(status -> cargarUsuario(request.getUsername().trim(), ahora));
        if (usuario == null) {
            // Usuario inexistente: se ejecuta igualmente una verificación PBKDF2 contra un hash ficticio para que
            // el tiempo de respuesta sea el mismo que con una contraseña incorrecta y no revele qué usuarios existen
            if (bandera == 1) {
                passwordEncoderUtil.simularVerificacion(request.getPassword());
            }
            throw new OnboardingException(CREDENCIALES_INVALIDAS, HttpStatus.UNAUTHORIZED, 401);
        }

        // Fase 2 (sin transacción): la verificación PBKDF2 es costosa a propósito y no debe retener
        // una conexión del pool mientras se calcula
        boolean credencialValida = bandera == 1
                ? passwordEncoderUtil.matches(request.getPassword(), usuario.getPasswordHash())
                : request.getFaceIdBiometrico().equals(usuario.getFaceIdBiometrico());

        if (!credencialValida) {
            transactionTemplate.executeWithoutResult(status -> usuarioLoginRepository.registrarIntentoFallido(
                    usuario.getId(), MAX_INTENTOS_FALLIDOS, ahora.plusMinutes(BLOQUEO_MINUTOS)));
            throw new OnboardingException(CREDENCIALES_INVALIDAS, HttpStatus.UNAUTHORIZED, 401);
        }

        // Solo tras validar la credencial se informa que el cliente está inactivo (evita revelar el estado a terceros)
        ClienteEntity cliente = usuario.getCliente();
        if (cliente != null && Boolean.FALSE.equals(cliente.getActivo())) {
            throw new OnboardingException("El cliente asociado a este usuario se encuentra inactivo.", HttpStatus.FORBIDDEN, 403);
        }

        // Migra hashes del esquema antiguo (SHA-256 con sal fija) a PBKDF2 (también fuera de la transacción)
        if (bandera == 1 && passwordEncoderUtil.requiereRecifrado(usuario.getPasswordHash())) {
            usuario.setPasswordHash(passwordEncoderUtil.encode(request.getPassword()));
        }

        // Fase 3 (transacción corta): is_logged_in = true, reiniciar intentos y actualizar última actividad
        usuario.setIntentosFallidos((short) 0);
        usuario.setBloqueadoHasta(null);
        usuario.setIsLoggedIn(true);
        usuario.setUltimaActividad(LocalDateTime.now());
        transactionTemplate.executeWithoutResult(status -> usuarioLoginRepository.save(usuario));

        String nombreCliente = cliente != null ? cliente.getNombre() + " " + cliente.getApellidoPaterno() : usuario.getUsername();

        return LoginResponse.builder()
                .username(usuario.getUsername())
                .nombreCliente(nombreCliente)
                .isLoggedIn(true)
                .ultimaActividad(usuario.getUltimaActividad())
                .mensaje("Inicio de sesión exitoso (" + (bandera == 1 ? "Por Contraseña" : "Por Face ID Biométrico") + ")")
                .build();
    }

    /** Devuelve el usuario (o null si no existe) con su cliente ya cargado para usarlo fuera de la transacción. */
    private UsuarioLoginEntity cargarUsuario(String username, LocalDateTime ahora) {
        UsuarioLoginEntity usuario = usuarioLoginRepository.findByUsername(username).orElse(null);
        if (usuario == null) {
            return null;
        }

        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora)) {
            long minutos = Math.max(1, Duration.between(ahora, usuario.getBloqueadoHasta()).toMinutes());
            throw new OnboardingException("Cuenta bloqueada temporalmente por exceso de intentos fallidos. Intente de nuevo en " + minutos + " minuto(s).", HttpStatus.LOCKED, 423);
        }

        // Verificar inactividad previa de 5 minutos si ya estaba logueado
        if (Boolean.TRUE.equals(usuario.getIsLoggedIn()) && usuario.getUltimaActividad() != null) {
            long minutosInactivo = Duration.between(usuario.getUltimaActividad(), ahora).toMinutes();
            if (minutosInactivo >= INACTIVIDAD_MINUTOS_MAX) {
                usuario.setIsLoggedIn(false);
                usuarioLoginRepository.save(usuario);
                log.info("Sesión expirada automáticamente para usuario {} por inactividad de {} min", username, minutosInactivo);
            }
        }

        // La relación con el cliente es LAZY: se inicializa mientras la sesión de Hibernate sigue abierta
        Hibernate.initialize(usuario.getCliente());
        return usuario;
    }

    @Transactional
    public void validarActividadYSesion(String username) {
        UsuarioLoginEntity usuario = usuarioLoginRepository.findByUsername(username)
                .orElseThrow(() -> new OnboardingException("Usuario no encontrado", HttpStatus.NOT_FOUND, 404));

        if (!Boolean.TRUE.equals(usuario.getIsLoggedIn())) {
            throw new OnboardingException("Debe iniciar sesión primero", HttpStatus.UNAUTHORIZED, 401);
        }

        if (usuario.getUltimaActividad() != null) {
            long minutos = Duration.between(usuario.getUltimaActividad(), LocalDateTime.now()).toMinutes();
            if (minutos >= INACTIVIDAD_MINUTOS_MAX) {
                usuario.setIsLoggedIn(false);
                usuarioLoginRepository.save(usuario);
                throw new OnboardingException("Sesión cerrada por inactividad superior a 5 minutos", HttpStatus.UNAUTHORIZED, 401);
            }
        }

        // Actualizar actividad
        usuario.setUltimaActividad(LocalDateTime.now());
        usuarioLoginRepository.save(usuario);
    }

    @Transactional
    @Scheduled(fixedRate = 60000)
    public void cerrarSesionesInactivasAutomaticamente() {
        List<UsuarioLoginEntity> usuariosActivos = usuarioLoginRepository.findByIsLoggedInTrue();
        LocalDateTime ahora = LocalDateTime.now();
        for (UsuarioLoginEntity u : usuariosActivos) {
            if (u.getUltimaActividad() != null) {
                long minutos = Duration.between(u.getUltimaActividad(), ahora).toMinutes();
                if (minutos >= INACTIVIDAD_MINUTOS_MAX) {
                    u.setIsLoggedIn(false);
                    usuarioLoginRepository.save(u);
                    log.info("Sesión inactiva cerrada automáticamente en segundo plano para usuario: {} (Inactivo {} min)", u.getUsername(), minutos);
                }
            }
        }
    }
}
