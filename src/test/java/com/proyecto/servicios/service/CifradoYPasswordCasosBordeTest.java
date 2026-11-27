package com.proyecto.servicios.service;

import com.proyecto.servicios.config.onboarding.PasswordEncoderUtil;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.service.Impl.CifradoServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Configuración inválida del cifrado AES y entradas anómalas del codificador de contraseñas. */
class CifradoYPasswordCasosBordeTest {

    private static String base64DeBytes(int n) {
        return Base64.getEncoder().encodeToString(new byte[n]);
    }

    @Test
    void llaveQueNoEsAes256SeRechaza() {
        CifradoServiceImpl servicio = new CifradoServiceImpl(base64DeBytes(16), base64DeBytes(16));

        IntegrationException ex = assertThrows(IntegrationException.class, () -> servicio.cifrar("hola"));
        assertEquals(ErrorCode.CONFIGURATION_ERROR, ex.getErrorCode());
    }

    @Test
    void ivDeLongitudIncorrectaSeRechaza() {
        CifradoServiceImpl servicio = new CifradoServiceImpl(base64DeBytes(32), base64DeBytes(8));

        assertThrows(IntegrationException.class, () -> servicio.cifrar("hola"));
    }

    @Test
    void llaveQueNoEsBase64SeRechaza() {
        CifradoServiceImpl servicio = new CifradoServiceImpl("esto no es base64!", base64DeBytes(16));

        IntegrationException ex = assertThrows(IntegrationException.class, () -> servicio.cifrar("hola"));
        assertEquals(ErrorCode.CONFIGURATION_ERROR, ex.getErrorCode());
    }

    @Test
    void cifraConConfiguracionValida() {
        CifradoServiceImpl servicio = new CifradoServiceImpl(base64DeBytes(32), base64DeBytes(16));

        String cifrado = servicio.cifrar("hola");

        assertNotEquals("hola", cifrado);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(cifrado));
    }

    // ---------- PasswordEncoderUtil ----------

    private final PasswordEncoderUtil encoder = new PasswordEncoderUtil();

    @Test
    void contrasenaNulaNoSeCifraNiCoincide() {
        assertNull(encoder.encode(null));
        assertFalse(encoder.matches(null, "hash"));
        assertFalse(encoder.requiereRecifrado(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pbkdf2$120000$no-es-base64!$xx", "pbkdf2$no-numero$AAAA$AAAA", "pbkdf2$solo$tres"})
    void hashMalformadoNuncaCoincide(String hash) {
        assertFalse(encoder.matches("Clave#123", hash));
    }

    @Test
    void simularVerificacionAceptaNulo() {
        assertDoesNotThrow(() -> encoder.simularVerificacion(null));
    }
}
