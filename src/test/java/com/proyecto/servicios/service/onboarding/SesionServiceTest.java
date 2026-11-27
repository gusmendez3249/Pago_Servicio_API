package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.TokenUtil;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.exception.NoAutenticadoException;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Autenticación por token: cada petición protegida debe traer un token vigente. */
@ExtendWith(MockitoExtension.class)
class SesionServiceTest {

    private static final String TOKEN = "Abcdefghijklmnopqrstuvwxyz0123456789_-ABCDE"; // 43 caracteres, como los reales

    @Mock
    private UsuarioLoginRepository usuarioLoginRepository;

    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks
    private SesionService sesionService;

    private ClienteEntity cliente;
    private UsuarioLoginEntity usuario;

    @BeforeEach
    void setUp() {
        cliente = ClienteEntity.builder().id(10L).activo(true).build();
        usuario = UsuarioLoginEntity.builder().id(1L).username("juan").rol("CLIENTE").cliente(cliente)
                .isLoggedIn(true).tokenHash(TokenUtil.hash(TOKEN))
                .tokenExpira(LocalDateTime.now().plusMinutes(2)).build();
    }

    @Test
    void tokenVigente_AutenticaYRenuevaLaSesion() {
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.of(usuario));

        SesionUsuario sesion = sesionService.autenticar("Bearer " + TOKEN);

        assertEquals("juan", sesion.username());
        assertEquals(10L, sesion.clienteId());
        assertFalse(sesion.esEjecutivo());
        // la sesión se extiende por actividad: de ~2 minutos restantes pasa a ~5
        assertTrue(usuario.getTokenExpira().isAfter(LocalDateTime.now().plusMinutes(4)));
        verify(usuarioLoginRepository).save(usuario);
    }

    @Test
    void esquemaBearerSinImportarMayusculas_EsValido() {
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.of(usuario));
        assertNotNull(sesionService.autenticar("bearer " + TOKEN));
        assertNotNull(sesionService.autenticar("BEARER   " + TOKEN));
    }

    @Test
    void ejecutivoSinCliente_TieneRolEjecutivoYSinClienteId() {
        UsuarioLoginEntity ejecutivo = UsuarioLoginEntity.builder().id(2L).username("admin").rol("EJECUTIVO")
                .isLoggedIn(true).tokenHash(TokenUtil.hash(TOKEN)).tokenExpira(LocalDateTime.now().plusMinutes(3)).build();
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.of(ejecutivo));

        SesionUsuario sesion = sesionService.autenticar("Bearer " + TOKEN);

        assertTrue(sesion.esEjecutivo());
        assertNull(sesion.clienteId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Bearer", "Bearer ", "Basic dXNlcjpwYXNz", "Token abc", "Bearer corto",
            "Bearer con espacios dentro del token", "Bearer <script>alert(1)</script>aaaaaaaaaaaaaaaaaaaa", "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void encabezadoAusenteOMalFormado_Lanza401SinConsultarLaBase(String encabezado) {
        NoAutenticadoException ex = assertThrows(NoAutenticadoException.class, () -> sesionService.autenticar(encabezado));
        assertEquals(401, ex.getCodigo());
        verifyNoInteractions(usuarioLoginRepository);
    }

    @Test
    void sinEncabezado_Lanza401() {
        NoAutenticadoException ex = assertThrows(NoAutenticadoException.class, () -> sesionService.autenticar(null));
        assertEquals(401, ex.getCodigo());
        assertTrue(ex.getMessage().contains("Authorization"));
    }

    @Test
    void tokenInexistente_Lanza401() {
        when(usuarioLoginRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        NoAutenticadoException ex = assertThrows(NoAutenticadoException.class, () -> sesionService.autenticar("Bearer " + TOKEN));
        assertEquals(401, ex.getCodigo());
    }

    @Test
    void tokenExpirado_Lanza401YLimpiaLaSesion() {
        usuario.setTokenExpira(LocalDateTime.now().minusSeconds(1));
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.of(usuario));

        NoAutenticadoException ex = assertThrows(NoAutenticadoException.class, () -> sesionService.autenticar("Bearer " + TOKEN));

        assertEquals(401, ex.getCodigo());
        assertNull(usuario.getTokenHash());
        assertFalse(usuario.getIsLoggedIn());
        verify(usuarioLoginRepository).save(usuario);
    }

    @Test
    void sesionCerradaPorInactividad_Lanza401AunqueElTokenNoHayaVencido() {
        usuario.setIsLoggedIn(false); // p. ej. cerrada por el proceso automático o por cambio de contraseña
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.of(usuario));

        NoAutenticadoException ex = assertThrows(NoAutenticadoException.class, () -> sesionService.autenticar("Bearer " + TOKEN));

        assertEquals(401, ex.getCodigo());
    }

    @Test
    void clienteDadoDeBaja_Lanza403() {
        cliente.setActivo(false);
        when(usuarioLoginRepository.findByTokenHash(TokenUtil.hash(TOKEN))).thenReturn(Optional.of(usuario));

        ClienteInactivoException ex = assertThrows(ClienteInactivoException.class, () -> sesionService.autenticar("Bearer " + TOKEN));

        assertEquals(403, ex.getCodigo());
    }

    @Test
    void elTokenEnClaroNuncaSeBuscaEnLaBase_SoloSuHash() {
        when(usuarioLoginRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThrows(NoAutenticadoException.class, () -> sesionService.autenticar("Bearer " + TOKEN));
        verify(usuarioLoginRepository).findByTokenHash(TokenUtil.hash(TOKEN));
        verify(usuarioLoginRepository, never()).findByTokenHash(TOKEN);
    }

    @Test
    void tokensGenerados_SonUnicosYSuHashTiene64Caracteres() {
        String a = TokenUtil.generar();
        String b = TokenUtil.generar();
        assertNotEquals(a, b);
        assertEquals(43, a.length());
        assertEquals(64, TokenUtil.hash(a).length());
        assertNotEquals(a, TokenUtil.hash(a));
    }
}
