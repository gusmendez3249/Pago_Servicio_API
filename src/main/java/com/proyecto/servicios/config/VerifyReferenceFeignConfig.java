package com.proyecto.servicios.config;

import feign.Client;
import feign.Request;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Configuración exclusiva de verifyReference. Sin @Configuration a propósito: solo la usa VerifyReferenceClient.
 * Es una consulta (no mueve dinero): lectura de 15 s, sin reintentos ni redirecciones y solo TLS 1.2 / 1.3.
 */
public class VerifyReferenceFeignConfig {

    @Bean
    public Request.Options verifyReferenceRequestOptions(
            @Value("${gestopago.verifyref.connect-timeout:5000}") long connectTimeout,
            @Value("${gestopago.verifyref.read-timeout:15000}") long readTimeout) {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                false
        );
    }

    @Bean
    public Retryer verifyReferenceRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public Client verifyReferenceFeignClient(TlsEstrictoSocketFactory gestoPagoSocketFactory) {
        // Fábrica TLS de GestoPagoSslConfig (truststore propio + TLS 1.3/1.2); hostnameVerifier nulo = el de la JVM
        return new Client.Default(gestoPagoSocketFactory, null);
    }
}
