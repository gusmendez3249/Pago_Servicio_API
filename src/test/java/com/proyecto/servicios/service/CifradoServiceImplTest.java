package com.proyecto.servicios.service;

import com.proyecto.servicios.service.Impl.CifradoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CifradoServiceImplTest {

    // Llave e IV de prueba generados al vuelo: las credenciales reales nunca van en el repositorio
    private static final String SECRET_KEY = aleatorio(32);
    private static final String IV = aleatorio(16);

    private static String aleatorio(int bytes) {
        byte[] valor = new byte[bytes];
        new java.security.SecureRandom().nextBytes(valor);
        return Base64.getEncoder().encodeToString(valor);
    }

    private CifradoServiceImpl cifradoService;

    @BeforeEach
    void setUp() {
        cifradoService = new CifradoServiceImpl(SECRET_KEY, IV);
    }

    @Test
    void testCifradoYDescifradoExitoso() throws Exception {
        String data = "{\"idServicio\":79, \"idProducto\":209 , \"referencia\": \"40425475190118187271\", \"montoPago\": 9999, \"telefono\":\"1111111111\", \"horaLocal\":\"20200401222821\"}";

        String encryptedBase64 = cifradoService.cifrar(data);
        assertNotNull(encryptedBase64);
        assertFalse(encryptedBase64.contains("\n"));
        assertFalse(encryptedBase64.contains("\r"));

        // Descifrado para verificar simetría y padding PKCS5/PKCS7
        byte[] keyBytes = Base64.getDecoder().decode(SECRET_KEY);
        byte[] ivBytes = Base64.getDecoder().decode(IV);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ivBytes));

        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
        String decrypted = new String(decryptedBytes, StandardCharsets.UTF_8);

        assertEquals(data, decrypted);
    }

    @Test
    void sinLlaveConfiguradaNoCifra() {
        CifradoServiceImpl sinLlave = new CifradoServiceImpl("", "");
        com.proyecto.servicios.exception.IntegrationException ex = assertThrows(
                com.proyecto.servicios.exception.IntegrationException.class, () -> sinLlave.cifrar("{}"));
        assertEquals(com.proyecto.servicios.enums.ErrorCode.CONFIGURATION_ERROR, ex.getErrorCode());
    }
}
