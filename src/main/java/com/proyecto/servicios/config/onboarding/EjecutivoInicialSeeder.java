package com.proyecto.servicios.config.onboarding;

import com.proyecto.servicios.dto.onboarding.LoginCredencialesDto;
import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import com.proyecto.servicios.service.onboarding.SesionUsuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Crea al arrancar el usuario EJECUTIVO inicial, si se configuró por variables de entorno:
 * {@code APP_EJECUTIVO_USUARIO} y {@code APP_EJECUTIVO_PASSWORD}. La contraseña nunca se guarda en el
 * código ni en application.properties y debe cumplir la misma política que cualquier otra.
 *
 * El ejecutivo puede operar sobre cualquier cliente; un cliente solo puede acceder a su propio registro.
 */
@Slf4j
@Component
public class EjecutivoInicialSeeder {

    private static final String PATRON_USUARIO = "^[A-Za-z0-9._-]{3,50}$";

    private final UsuarioLoginRepository usuarioLoginRepository;
    private final PasswordEncoderUtil passwordEncoderUtil;
    private final String usuario;
    private final String password;

    public EjecutivoInicialSeeder(UsuarioLoginRepository usuarioLoginRepository,
                                  PasswordEncoderUtil passwordEncoderUtil,
                                  @Value("${app.ejecutivo.usuario:}") String usuario,
                                  @Value("${app.ejecutivo.password:}") String password) {
        this.usuarioLoginRepository = usuarioLoginRepository;
        this.passwordEncoderUtil = passwordEncoderUtil;
        this.usuario = usuario;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crearEjecutivoInicial() {
        if (!configuracionValida()) {
            return;
        }

        Optional<UsuarioLoginEntity> existente = usuarioLoginRepository.findByUsername(usuario);
        if (existente.isPresent()) {
            if (!SesionUsuario.ROL_EJECUTIVO.equals(existente.get().getRol())) {
                log.error("El usuario '{}' ya existe y no es ejecutivo; no se modificó.", usuario);
            }
            return;
        }

        usuarioLoginRepository.save(UsuarioLoginEntity.builder()
                .username(usuario)
                .passwordHash(passwordEncoderUtil.encode(password))
                .rol(SesionUsuario.ROL_EJECUTIVO)
                .isLoggedIn(false)
                .build());
        log.info("Usuario ejecutivo '{}' creado.", usuario);
    }

    /** Verifica que las variables de entorno existan y cumplan las mismas reglas que cualquier usuario. */
    private boolean configuracionValida() {
        if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
            log.warn("No hay usuario ejecutivo configurado (APP_EJECUTIVO_USUARIO / APP_EJECUTIVO_PASSWORD): "
                    + "solo los clientes podrán consultar o actualizar su propio registro.");
            return false;
        }
        if (!usuario.matches(PATRON_USUARIO)) {
            log.error("APP_EJECUTIVO_USUARIO no es válido (3 a 50 caracteres: letras, números, punto, guion o guion bajo). No se creó el ejecutivo.");
            return false;
        }
        if (!password.matches(LoginCredencialesDto.POLITICA_PASSWORD)) {
            log.error("APP_EJECUTIVO_PASSWORD no cumple la política (8 a 72 caracteres, 1 mayúscula, 1 minúscula, 1 número, 1 carácter especial, sin espacios). No se creó el ejecutivo.");
            return false;
        }
        return true;
    }
}
