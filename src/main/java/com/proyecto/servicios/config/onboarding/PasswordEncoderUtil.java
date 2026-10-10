package com.proyecto.servicios.config.onboarding;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hash de contraseñas con PBKDF2-HMAC-SHA256 y sal aleatoria por usuario.
 * Formato almacenado: pbkdf2$iteraciones$salBase64$hashBase64
 *
 * Los hashes antiguos (SHA-256 con sal fija, sin prefijo) se siguen aceptando para no dejar
 * fuera a los usuarios existentes; AuthService los re-cifra con PBKDF2 en su siguiente login.
 */
@Component
public class PasswordEncoderUtil {

    private static final String PREFIJO = "pbkdf2";
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACIONES = 120_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;

    private static final String SALT_LEGADO = "OnboardingPagoServiciosSalt2026";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String hashFicticio = encode("hash-ficticio-para-tiempo-constante");

    public String encode(String rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        byte[] sal = new byte[BYTES_SAL];
        RANDOM.nextBytes(sal);
        byte[] hash = pbkdf2(rawPassword, sal, ITERACIONES);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIJO + "$" + ITERACIONES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        if (!encodedPassword.startsWith(PREFIJO + "$")) {
            return compararTiempoConstante(encodeLegado(rawPassword), encodedPassword);
        }
        String[] partes = encodedPassword.split("\\$");
        if (partes.length != 4) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            return MessageDigest.isEqual(esperado, pbkdf2(rawPassword, sal, iteraciones));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Ejecuta una verificación completa contra un hash ficticio. Se usa cuando el usuario no existe para que
     * la respuesta tarde lo mismo que con una contraseña incorrecta (evita enumerar usuarios por tiempo).
     */
    public void simularVerificacion(String rawPassword) {
        matches(rawPassword != null ? rawPassword : "", hashFicticio);
    }

    /** true si el hash almacenado usa el esquema antiguo y debe re-cifrarse. */
    public boolean requiereRecifrado(String encodedPassword) {
        return encodedPassword != null && !encodedPassword.startsWith(PREFIJO + "$" + ITERACIONES + "$");
    }

    private byte[] pbkdf2(String rawPassword, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), sal, iteraciones, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error en algoritmo de cifrado", e);
        } finally {
            spec.clearPassword();
        }
    }

    private String encodeLegado(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((SALT_LEGADO + rawPassword).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error en algoritmo de cifrado", e);
        }
    }

    private boolean compararTiempoConstante(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
