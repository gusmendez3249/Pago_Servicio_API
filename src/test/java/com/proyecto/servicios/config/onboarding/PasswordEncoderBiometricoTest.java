package com.proyecto.servicios.config.onboarding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderBiometricoTest {

    private final PasswordEncoderUtil encoder = new PasswordEncoderUtil();

    @Test
    void elFaceIdSeGuardaComoHashConSalYSeVerifica() {
        String hash = encoder.encodeBiometrico(9876543210L);

        assertTrue(hash.startsWith("pbkdf2$"));
        assertFalse(hash.contains("9876543210"), "el valor nunca queda en claro");
        assertNotEquals(hash, encoder.encodeBiometrico(9876543210L), "sal aleatoria por valor");
        assertTrue(encoder.matchesBiometrico(9876543210L, hash));
        assertFalse(encoder.matchesBiometrico(9876543211L, hash));
    }

    @Test
    void valoresNulosOHashInvalidoNoCoinciden() {
        assertNull(encoder.encodeBiometrico(null));
        assertFalse(encoder.matchesBiometrico(null, encoder.encodeBiometrico(1L)));
        assertFalse(encoder.matchesBiometrico(1L, null));
        assertFalse(encoder.matchesBiometrico(1L, "1"), "un valor en claro heredado no se acepta");
    }
}
