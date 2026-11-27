package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.dto.onboarding.LoginRequest;
import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Expiración de sesiones por inactividad (al iniciar sesión y en el proceso programado) y bloqueo temporal. */
@ExtendWith(MockitoExtension.class)
class AuthServiceInactividadTest {

    @Mock private UsuarioLoginRepository usuarioLoginRepository;
    @Mock private PasswordEncoderUtil passwordEncoderUtil;
    @Spy private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks private AuthService authService;

    private static UsuarioLoginEntity usuario(String username, LocalDateTime ultimaActividad) {
        return UsuarioLoginEntity.builder().id(1L).username(username).passwordHash("hash")
                .isLoggedIn(true).ultimaActividad(ultimaActividad).build();
    }

    @Test
    void procesoProgramadoCierraSoloLasSesionesInactivas() {
        UsuarioLoginEntity inactivo = usuario("inactivo", LocalDateTime.now().minusMinutes(10));
        UsuarioLoginEntity activo = usuario("activo", LocalDateTime.now().minusMinutes(1));
        UsuarioLoginEntity sinActividad = usuario("nuevo", null);
        when(usuarioLoginRepository.findByIsLoggedInTrue()).thenReturn(List.of(inactivo, activo, sinActividad));

        authService.cerrarSesionesInactivasAutomaticamente();

        assertFalse(inactivo.getIsLoggedIn());
        assertTrue(activo.getIsLoggedIn());
        assertTrue(sinActividad.getIsLoggedIn());
        verify(usuarioLoginRepository).save(inactivo);
        verify(usuarioLoginRepository, never()).save(activo);
    }

    @Test
    void alIniciarSesionSeExpiraLaSesionAnteriorInactiva() {
        UsuarioLoginEntity juan = usuario("juan", LocalDateTime.now().minusMinutes(30));
        when(usuarioLoginRepository.findByUsername("juan")).thenReturn(Optional.of(juan));
        when(passwordEncoderUtil.matches("Clave#123", "hash")).thenReturn(true);

        authService.login(LoginRequest.builder().bandera(1).username("juan").password("Clave#123").build());

        verify(usuarioLoginRepository, atLeast(2)).save(juan);
        assertTrue(juan.getIsLoggedIn(), "tras expirar la anterior se abre la sesión nueva");
    }

    @Test
    void cuentaBloqueadaInformaLosMinutosRestantes() {
        UsuarioLoginEntity bloqueado = usuario("juan", null);
        bloqueado.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        when(usuarioLoginRepository.findByUsername("juan")).thenReturn(Optional.of(bloqueado));
        LoginRequest request = LoginRequest.builder().bandera(1).username("juan").password("Clave#123").build();

        CuentaBloqueadaException ex = assertThrows(CuentaBloqueadaException.class, () -> authService.login(request));

        assertTrue(ex.getMessage().contains("minuto"));
        verify(passwordEncoderUtil, never()).matches(any(), any());
    }
}
