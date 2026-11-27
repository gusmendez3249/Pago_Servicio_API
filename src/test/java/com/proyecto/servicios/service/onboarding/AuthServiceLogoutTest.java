package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.config.onboarding.TokenUtil;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.exception.NoAutenticadoException;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Cierre de sesión: el token deja de servir de inmediato. Usa un SesionService real sobre el repositorio simulado. */
@ExtendWith(MockitoExtension.class)
class AuthServiceLogoutTest {

    private static final String TOKEN = "Abcdefghijklmnopqrstuvwxyz0123456789_-ABCDE";

    @Mock private UsuarioLoginRepository usuarioLoginRepository;
    @Mock private PasswordEncoderUtil passwordEncoderUtil;

    private AuthService authService;
    private SesionService sesionService;
    private UsuarioLoginEntity usuario;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        sesionService = new SesionService(usuarioLoginRepository, transactionTemplate);
        authService = new AuthService(usuarioLoginRepository, passwordEncoderUtil, transactionTemplate, sesionService);

        usuario = UsuarioLoginEntity.builder().id(1L).username("juan").rol("CLIENTE")
                .cliente(ClienteEntity.builder().id(10L).activo(true).build())
                .isLoggedIn(true).tokenHash(TokenUtil.hash(TOKEN))
                .tokenExpira(LocalDateTime.now().plusMinutes(3)).build();
    }

    @Test
    void logoutConTokenValido_LimpiaLaSesion() {
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.of(usuario));
        when(usuarioLoginRepository.findById(1L)).thenReturn(Optional.of(usuario));

        authService.logout("Bearer " + TOKEN);

        assertFalse(usuario.getIsLoggedIn());
        assertNull(usuario.getTokenHash());
        assertNull(usuario.getTokenExpira());
        verify(usuarioLoginRepository, atLeastOnce()).save(usuario);
    }

    @Test
    void logoutSinToken_Lanza401SinTocarLaBase() {
        NoAutenticadoException ex = assertThrows(NoAutenticadoException.class, () -> authService.logout(null));
        assertEquals(401, ex.getCodigo());
        verifyNoInteractions(usuarioLoginRepository);
    }

    @Test
    void logoutConTokenInexistente_Lanza401() {
        when(usuarioLoginRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThrows(NoAutenticadoException.class, () -> authService.logout("Bearer " + TOKEN));
        verify(usuarioLoginRepository, never()).findById(any());
    }

    @Test
    void logoutConTokenVencido_Lanza401() {
        usuario.setTokenExpira(LocalDateTime.now().minusSeconds(1));
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.of(usuario));

        assertThrows(NoAutenticadoException.class, () -> authService.logout("Bearer " + TOKEN));
        assertNull(usuario.getTokenHash());
    }

    @Test
    void trasElLogout_ElTokenYaNoAutentica() {
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.of(usuario));
        when(usuarioLoginRepository.findById(1L)).thenReturn(Optional.of(usuario));
        assertNotNull(sesionService.autenticar("Bearer " + TOKEN));

        authService.logout("Bearer " + TOKEN);

        // la base ya no encuentra el hash porque se limpió; se simula el comportamiento real del repositorio
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.empty());
        assertThrows(NoAutenticadoException.class, () -> sesionService.autenticar("Bearer " + TOKEN));
    }

    @Test
    void procesoProgramado_TambienLimpiaTokenYExpiracion() {
        usuario.setUltimaActividad(LocalDateTime.now().minusMinutes(10));
        when(usuarioLoginRepository.findByIsLoggedInTrue()).thenReturn(List.of(usuario));

        authService.cerrarSesionesInactivasAutomaticamente();

        assertFalse(usuario.getIsLoggedIn());
        assertNull(usuario.getTokenHash());
        assertNull(usuario.getTokenExpira());
    }
}
