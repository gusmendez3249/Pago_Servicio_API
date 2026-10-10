package com.proyecto.servicios.config.onboarding;

import com.proyecto.servicios.dto.onboarding.LoginCredencialesDto;
import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Crea al arrancar el usuario EJECUTIVO inicial, si se configuró por variables de entorno:
 * {@code APP_EJECUTIVO_USUARIO} y {@code APP_EJECUTIVO_PASSWORD}. La contraseña nunca se guarda en el
 * código ni en application.properties y debe cumplir la misma política que cualquier otra.
 *
 * El ejecutivo puede operar sobre cualquier cliente; un cliente solo puede acceder a su propio registro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EjecutivoInicialSeeder {

    private final UsuarioLoginRepository usuarioLoginRepository;
    private final PasswordEncoderUtil passwordEncoderUtil;

    @Value("${app.ejecutivo.usuario:}")
    private String usuario;

    @Value("${app.ejecutivo.password:}")
    private String password;

    @EventListener(ApplicationReadyEvent.class)
    public void crearEjecutivoInicial() {
        if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
            log.warn("No hay usuario ejecutivo configurado (APP_EJECUTIVO_USUARIO / APP_EJECUTIVO_PASSWORD): "
                    + "solo los clientes podrán consultar o actualizar su propio registro.");
            return;
        }
        if (!usuario.matches("^[A-Za-z0-9._-]{3,50}$")) {
            log.error("APP_EJECUTIVO_USUARIO no es válido (3 a 50 caracteres: letras, números, punto, guion o guion bajo). No se creó el ejecutivo.");
            return;
        }
        if (!password.matches(LoginCredencialesDto.POLITICA_PASSWORD)) {
            log.error("APP_EJECUTIVO_PASSWORD no cumple la política (8 a 72 caracteres, 1 mayúscula, 1 minúscula, 1 número, 1 carácter especial, sin espacios). No se creó el ejecutivo.");
            return;
        }

        UsuarioLoginEntity existente = usuarioLoginRepository.findByUsername(usuario).orElse(null);
        if (existente != null) {
            if (!"EJECUTIVO".equals(existente.getRol())) {
                log.error("El usuario '{}' ya existe y no es ejecutivo; no se modificó.", usuario);
            }
            return;
        }

        usuarioLoginRepository.save(UsuarioLoginEntity.builder()
                .username(usuario)
                .passwordHash(passwordEncoderUtil.encode(password))
                .rol("EJECUTIVO")
                .isLoggedIn(false)
                .build());
        log.info("Usuario ejecutivo '{}' creado.", usuario);
    }
}
