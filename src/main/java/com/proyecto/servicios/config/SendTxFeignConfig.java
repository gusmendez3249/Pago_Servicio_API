package com.proyecto.servicios.config;

import feign.Client;
import feign.Request;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Configuración exclusiva de sendTx. Sin @Configuration a propósito: solo la usa SendTxClient y no debe volverse global.
 *
 * - Timeouts: GestoPago espera hasta 62 s antes de rendirse (CODIGO 82), así que la lectura debe durar más que eso;
 *   con el timeout global de 10 s se cortaría una transacción que aún puede aplicarse.
 * - Sin reintentos ni redirecciones automáticas: reenviar un POST financiero podría duplicar el cargo.
 * - Solo TLS 1.2 / 1.3, con validación normal del certificado del servidor.
 */
public class SendTxFeignConfig {

    @Bean
    public Request.Options sendTxRequestOptions(
            @Value("${gestopago.sendtx.connect-timeout:5000}") long connectTimeout,
            @Value("${gestopago.sendtx.read-timeout:65000}") long readTimeout) {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                false
        );
    }

    @Bean
    public Retryer sendTxRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public Client sendTxFeignClient() {
        // hostnameVerifier nulo = verificador por defecto de la JVM (no se desactiva la validación del nombre)
        return new Client.Default(new TlsEstrictoSocketFactory(), null);
    }
}
