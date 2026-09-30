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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioLoginRepository usuarioLoginRepository;
    private final PasswordEncoderUtil passwordEncoderUtil;

    private static final int INACTIVIDAD_MINUTOS_MAX = 5;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new OnboardingException("El nombre de usuario es obligatorio.", HttpStatus.BAD_REQUEST, 400);
        }

        UsuarioLoginEntity usuario = usuarioLoginRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new OnboardingException("Usuario no encontrado.", HttpStatus.NOT_FOUND, 404));

        ClienteEntity cliente = usuario.getCliente();
        if (cliente != null && Boolean.FALSE.equals(cliente.getActivo())) {
            throw new OnboardingException("El cliente asociado a este usuario se encuentra inactivo.", HttpStatus.FORBIDDEN, 403);
        }

        // Verificar inactividad previa de 5 minutos si ya estaba logueado
        if (Boolean.TRUE.equals(usuario.getIsLoggedIn()) && usuario.getUltimaActividad() != null) {
            long minutosInactivo = Duration.between(usuario.getUltimaActividad(), LocalDateTime.now()).toMinutes();
            if (minutosInactivo >= INACTIVIDAD_MINUTOS_MAX) {
                usuario.setIsLoggedIn(false);
                usuarioLoginRepository.save(usuario);
                log.info("Sesión expirada automáticamente para usuario {} por inactividad de {} min", request.getUsername(), minutosInactivo);
            }
        }

        Integer bandera = request.getBandera();
        if (bandera == null) {
            throw new OnboardingException("La bandera de autenticación es obligatoria (1 = Contraseña, 2 = Biométrico Face ID).", HttpStatus.BAD_REQUEST, 400);
        }

        if (bandera == 1) {
            // Autenticación por contraseña
            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                throw new OnboardingException("La contraseña es requerida para autenticación por contraseña.", HttpStatus.BAD_REQUEST, 400);
            }
            if (!passwordEncoderUtil.matches(request.getPassword(), usuario.getPasswordHash())) {
                throw new OnboardingException("Credenciales inválidas. Contraseña incorrecta.", HttpStatus.UNAUTHORIZED, 401);
            }
        } else if (bandera == 2) {
            // Autenticación por Face ID biométrico
            if (request.getFaceIdBiometrico() == null) {
                throw new OnboardingException("El identificador biométrico Face ID es requerido para autenticación por biométrico.", HttpStatus.BAD_REQUEST, 400);
            }
            if (!request.getFaceIdBiometrico().equals(usuario.getFaceIdBiometrico())) {
                throw new OnboardingException("Autenticación biométrica fallida. Face ID no coincide.", HttpStatus.UNAUTHORIZED, 401);
            }
        } else {
            throw new OnboardingException("Bandera de autenticación no válida: " + bandera + ". Use 1 (Contraseña) o 2 (Biométrico).", HttpStatus.BAD_REQUEST, 400);
        }

        // Éxito: Establecer is_logged_in = true y actualizar fecha de última actividad
        usuario.setIsLoggedIn(true);
        usuario.setUltimaActividad(LocalDateTime.now());
        usuarioLoginRepository.save(usuario);

        String nombreCliente = cliente != null ? cliente.getNombre() + " " + cliente.getApellidoPaterno() : usuario.getUsername();
        Long clienteId = cliente != null ? cliente.getId() : null;

        return LoginResponse.builder()
                .username(usuario.getUsername())
                .clienteId(clienteId)
                .nombreCliente(nombreCliente)
                .isLoggedIn(true)
                .ultimaActividad(usuario.getUltimaActividad())
                .mensaje("Inicio de sesión exitoso (" + (bandera == 1 ? "Por Contraseña" : "Por Face ID Biométrico") + ")")
                .build();
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
}
