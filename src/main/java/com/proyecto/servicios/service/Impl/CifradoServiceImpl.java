package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.service.CifradoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Slf4j
@Service
public class CifradoServiceImpl implements CifradoService {

    private static final String TRANSFORMACION = "AES/CBC/PKCS5Padding";
    private static final int BYTES_LLAVE_AES_256 = 32;
    private static final int BYTES_IV = 16;

    private final String secretKeyBase64;
    private final String ivBase64;

    public CifradoServiceImpl(
            @Value("${gestopago.crypto.secret-key:}") String secretKeyBase64,
            @Value("${gestopago.crypto.iv:}") String ivBase64) {
        this.secretKeyBase64 = secretKeyBase64;
        this.ivBase64 = ivBase64;
    }

    @Override
    public String cifrar(String textoPlano) {
        byte[] llave = decodificar(secretKeyBase64, "SECRET_KEY");
        byte[] iv = decodificar(ivBase64, "IV");

        if (llave.length != BYTES_LLAVE_AES_256 || iv.length != BYTES_IV) {
            log.error("SECRET_KEY debe medir {} bytes (AES-256) e IV {} bytes", BYTES_LLAVE_AES_256, BYTES_IV);
            throw new IntegrationException(ErrorCode.CONFIGURATION_ERROR);
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(llave, "AES"), new IvParameterSpec(iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(cifrado);
        } catch (GeneralSecurityException exception) {
            log.error("Error al cifrar la petición hacia GestoPago");
            throw new IntegrationException(ErrorCode.INTERNAL_ERROR, exception);
        }
    }

    private byte[] decodificar(String valorBase64, String nombre) {
        if (valorBase64 == null || valorBase64.isBlank()) {
            log.error("Configuración de cifrado de GestoPago incompleta: falta {}", nombre);
            throw new IntegrationException(ErrorCode.CONFIGURATION_ERROR);
        }
        try {
            return Base64.getDecoder().decode(valorBase64.trim());
        } catch (IllegalArgumentException exception) {
            log.error("Configuración de cifrado de GestoPago inválida: {} no es Base64", nombre);
            throw new IntegrationException(ErrorCode.CONFIGURATION_ERROR, exception);
        }
    }
}
