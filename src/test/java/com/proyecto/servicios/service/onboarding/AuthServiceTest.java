package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.dto.onboarding.LoginRequest;
import com.proyecto.servicios.dto.onboarding.LoginResponse;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.exception.OnboardingException;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioLoginRepository usuarioLoginRepository;

    @Mock
    private PasswordEncoderUtil passwordEncoderUtil;

    // TransactionTemplate real sobre un gestor de transacciones simulado: ejecuta el callback tal cual
    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks
    private AuthService authService;

    private UsuarioLoginEntity usuarioMock;

    @BeforeEach
    void setUp() {
        ClienteEntity clienteMock = ClienteEntity.builder()
                .id(1L)
                .nombre("Juan")
                .apellidoPaterno("Pérez")
                .activo(true)
                .build();

        usuarioMock = UsuarioLoginEntity.builder()
                .id(1L)
                .username("juan_perez")
                .passwordHash("encoded_hash")
                .faceIdBiometrico(9876543210L)
                .isLoggedIn(false)
                .cliente(clienteMock)
                .build();
    }

    @Test
    void login_ConBandera1_ContrasenaCorrecta_Exito() {
        LoginRequest request = LoginRequest.builder()
                .bandera(1)
                .username("juan_perez")
                .password("Password123!")
                .build();

        when(usuarioLoginRepository.findByUsername("juan_perez")).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoderUtil.matches("Password123!", "encoded_hash")).thenReturn(true);
        when(usuarioLoginRepository.save(any(UsuarioLoginEntity.class))).thenReturn(usuarioMock);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertTrue(response.getIsLoggedIn());
        assertEquals("juan_perez", response.getUsername());
        verify(usuarioLoginRepository, times(1)).save(usuarioMock);
    }

    @Test
    void login_ConBandera2_FaceIdCorrecto_Exito() {
        LoginRequest request = LoginRequest.builder()
                .bandera(2)
                .username("juan_perez")
                .faceIdBiometrico(9876543210L)
                .build();

        when(usuarioLoginRepository.findByUsername("juan_perez")).thenReturn(Optional.of(usuarioMock));
        when(usuarioLoginRepository.save(any(UsuarioLoginEntity.class))).thenReturn(usuarioMock);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertTrue(response.getIsLoggedIn());
        assertEquals("juan_perez", response.getUsername());
        verify(usuarioLoginRepository, times(1)).save(usuarioMock);
    }

    @Test
    void login_UsuarioInexistente_LanzaExcepcion() {
        LoginRequest request = LoginRequest.builder()
                .bandera(1)
                .username("inexistente")
                .password("1234")
                .build();

        when(usuarioLoginRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        OnboardingException ex = assertThrows(OnboardingException.class, () -> authService.login(request));
        // Mismo código y mensaje que una contraseña incorrecta: no revela si el usuario existe
        assertEquals(401, ex.getCodigo());
        assertEquals("Credenciales inválidas.", ex.getMessage());
    }

    @Test
    void login_ContrasenaIncorrecta_RegistraIntentoFallido() {
        LoginRequest request = LoginRequest.builder()
                .bandera(1).username("juan_perez").password("mala").build();
        when(usuarioLoginRepository.findByUsername("juan_perez")).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoderUtil.matches("mala", "encoded_hash")).thenReturn(false);

        OnboardingException ex = assertThrows(OnboardingException.class, () -> authService.login(request));

        assertEquals(401, ex.getCodigo());
        assertEquals("Credenciales inválidas.", ex.getMessage());
        verify(usuarioLoginRepository).registrarIntentoFallido(eq(1L), eq((short) 5), any());
    }

    @Test
    void login_CuentaBloqueada_Lanza423SinVerificarCredencial() {
        usuarioMock.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        LoginRequest request = LoginRequest.builder()
                .bandera(1).username("juan_perez").password("Password123!").build();
        when(usuarioLoginRepository.findByUsername("juan_perez")).thenReturn(Optional.of(usuarioMock));

        OnboardingException ex = assertThrows(OnboardingException.class, () -> authService.login(request));

        assertEquals(423, ex.getCodigo());
        verify(passwordEncoderUtil, never()).matches(any(), any());
    }

    @Test
    void login_ClienteInactivo_ConCredencialIncorrecta_NoRevelaEstado() {
        usuarioMock.getCliente().setActivo(false);
        LoginRequest request = LoginRequest.builder()
                .bandera(1).username("juan_perez").password("mala").build();
        when(usuarioLoginRepository.findByUsername("juan_perez")).thenReturn(Optional.of(usuarioMock));

        OnboardingException ex = assertThrows(OnboardingException.class, () -> authService.login(request));

        assertEquals(401, ex.getCodigo());
    }
}
