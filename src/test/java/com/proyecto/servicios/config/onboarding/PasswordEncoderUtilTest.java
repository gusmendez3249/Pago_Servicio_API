package com.proyecto.servicios.config.onboarding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderUtilTest {

    private final PasswordEncoderUtil encoder = new PasswordEncoderUtil();

    @Test
    void encode_MismaContrasena_GeneraHashesDistintos() {
        String h1 = encoder.encode("Password123!");
        String h2 = encoder.encode("Password123!");

        assertNotEquals(h1, h2, "Con sal aleatoria dos usuarios con la misma contraseña no deben compartir hash");
        assertTrue(encoder.matches("Password123!", h1));
        assertTrue(encoder.matches("Password123!", h2));
        assertFalse(encoder.matches("Password124!", h1));
        assertFalse(encoder.requiereRecifrado(h1));
    }

    @Test
    void matches_HashLegadoSha256_SigueValidoYRequiereRecifrado() {
        // Hash generado por la versión anterior (SHA-256 + sal fija) para "Password123!"
        String legado = "L6CyCp+nmY1/nH/1jt+bENTe7UKtwZMFSRyICwKG/qE=";

        assertTrue(encoder.matches("Password123!", legado));
        assertFalse(encoder.matches("otra", legado));
        assertTrue(encoder.requiereRecifrado(legado));
    }

    @Test
    void matches_HashMalformado_DevuelveFalse() {
        assertFalse(encoder.matches("x", "pbkdf2$abc$$"));
        assertFalse(encoder.matches("x", null));
        assertFalse(encoder.matches(null, "pbkdf2$1$AA==$AA=="));
    }
}
