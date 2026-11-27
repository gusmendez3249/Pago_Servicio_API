package com.proyecto.servicios.config.onboarding;

import com.proyecto.servicios.entity.onboarding.UsuarioLoginEntity;
import com.proyecto.servicios.repositorys.onboarding.UsuarioLoginRepository;
import com.proyecto.servicios.service.onboarding.SesionUsuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Alta del ejecutivo inicial a partir de variables de entorno. */
@ExtendWith(MockitoExtension.class)
class EjecutivoInicialSeederTest {

    private static final String USUARIO = "ejecutivo.admin";
    private static final String PASSWORD_VALIDA = "Segura#2026";

    @Mock private UsuarioLoginRepository repository;
    @Mock private PasswordEncoderUtil encoder;

    private EjecutivoInicialSeeder seeder(String usuario, String password) {
        return new EjecutivoInicialSeeder(repository, encoder, usuario, password);
    }

    @ParameterizedTest(name = "usuario=[{0}] password=[{1}]")
    @CsvSource(value = {
            "NULL, Segura#2026",
            "'', Segura#2026",
            "ejecutivo.admin, NULL",
            "ejecutivo.admin, '  '",
            "ab, Segura#2026",
            "'usuario con espacios', Segura#2026",
            "ejecutivo.admin, debil"
    }, nullValues = "NULL")
    void configuracionAusenteOInvalidaNoCreaNada(String usuario, String password) {
        seeder(usuario, password).crearEjecutivoInicial();

        verifyNoInteractions(repository, encoder);
    }

    @Test
    void creaElEjecutivoConLaContrasenaCifrada() {
        when(repository.findByUsername(USUARIO)).thenReturn(Optional.empty());
        when(encoder.encode(PASSWORD_VALIDA)).thenReturn("hash");

        seeder(USUARIO, PASSWORD_VALIDA).crearEjecutivoInicial();

        ArgumentCaptor<UsuarioLoginEntity> guardado = ArgumentCaptor.forClass(UsuarioLoginEntity.class);
        verify(repository).save(guardado.capture());
        assertEquals(USUARIO, guardado.getValue().getUsername());
        assertEquals("hash", guardado.getValue().getPasswordHash());
        assertEquals(SesionUsuario.ROL_EJECUTIVO, guardado.getValue().getRol());
        assertFalse(guardado.getValue().getIsLoggedIn());
    }

    @Test
    void ejecutivoExistenteNoSeDuplica() {
        UsuarioLoginEntity existente = UsuarioLoginEntity.builder().username(USUARIO)
                .rol(SesionUsuario.ROL_EJECUTIVO).build();
        when(repository.findByUsername(USUARIO)).thenReturn(Optional.of(existente));
        when(encoder.matches(eq(PASSWORD_VALIDA), any())).thenReturn(true);

        seeder(USUARIO, PASSWORD_VALIDA).crearEjecutivoInicial();

        verify(repository, never()).save(any());
    }

    @Test
    void contrasenaNuevaRotaElHashYCierraLaSesion() {
        UsuarioLoginEntity existente = UsuarioLoginEntity.builder().username(USUARIO).passwordHash("viejo")
                .tokenHash("token").isLoggedIn(true).rol(SesionUsuario.ROL_EJECUTIVO).build();
        when(repository.findByUsername(USUARIO)).thenReturn(Optional.of(existente));
        when(encoder.matches(PASSWORD_VALIDA, "viejo")).thenReturn(false);
        when(encoder.encode(PASSWORD_VALIDA)).thenReturn("nuevo");

        seeder(USUARIO, PASSWORD_VALIDA).crearEjecutivoInicial();

        verify(repository).save(existente);
        assertEquals("nuevo", existente.getPasswordHash());
        assertNull(existente.getTokenHash());
        assertFalse(existente.getIsLoggedIn());
    }

    @Test
    void contrasenaPublicadaSeRechazaSinTocarNada() {
        String publicada = "Publicada#2026";
        new EjecutivoInicialSeeder(repository, encoder, USUARIO, publicada,
                Set.of(EjecutivoInicialSeeder.sha256(publicada))).crearEjecutivoInicial();

        verifyNoInteractions(repository, encoder);
    }

    @Test
    void laListaDePublicadasSoloGuardaHashes() {
        assertTrue(EjecutivoInicialSeeder.PASSWORDS_PUBLICADAS.stream().allMatch(h -> h.matches("^[0-9a-f]{64}$")));
        assertEquals(64, EjecutivoInicialSeeder.sha256("x").length());
    }

    @Test
    void usuarioExistenteConOtroRolNoSeModifica() {
        UsuarioLoginEntity cliente = UsuarioLoginEntity.builder().username(USUARIO)
                .rol(SesionUsuario.ROL_CLIENTE).build();
        when(repository.findByUsername(USUARIO)).thenReturn(Optional.of(cliente));

        seeder(USUARIO, PASSWORD_VALIDA).crearEjecutivoInicial();

        verify(repository, never()).save(any());
        assertEquals(SesionUsuario.ROL_CLIENTE, cliente.getRol());
    }
}
