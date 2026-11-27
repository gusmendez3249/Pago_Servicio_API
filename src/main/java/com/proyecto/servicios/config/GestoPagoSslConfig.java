package com.proyecto.servicios.config;

import feign.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * SSL/TLS hacia GestoPago, activado desde application.properties.
 *
 * - gestopago.ssl.enabled=true: se usa el SSL bundle {@code gestopago.ssl.bundle} (truststore con el certificado
 *   de gestopago.portalventas.net y su cadena DigiCert). false: se usa el cacerts por defecto de la JVM.
 * - gestopago.ssl.protocolos: versiones de TLS que negocia el cliente (solo se admiten TLSv1.3 y TLSv1.2).
 *
 * El {@link Client} de aquí es el de todos los clientes Feign (authenticate, getProductList); sendTx y
 * verifyReference definen el suyo, pero con esta misma fábrica de sockets.
 */
@Configuration
public class GestoPagoSslConfig {

    private static final Logger log = LoggerFactory.getLogger(GestoPagoSslConfig.class);

    @Bean
    public TlsEstrictoSocketFactory gestoPagoSocketFactory(
            SslBundles sslBundles,
            @Value("${gestopago.ssl.enabled:true}") boolean sslHabilitado,
            @Value("${gestopago.ssl.bundle:gestopago}") String nombreBundle,
            @Value("${gestopago.ssl.protocolos:TLSv1.3,TLSv1.2}") String[] protocolos) {
        TlsEstrictoSocketFactory fabrica;
        if (sslHabilitado) {
            SslBundle bundle = sslBundles.getBundle(nombreBundle);
            fabrica = new TlsEstrictoSocketFactory(bundle.createSslContext(), protocolos);
            log.info("TLS hacia GestoPago con el truststore del bundle '{}', protocolos {}",
                    nombreBundle, Arrays.toString(fabrica.getProtocolos()));
        } else {
            fabrica = new TlsEstrictoSocketFactory(TlsEstrictoSocketFactory.contextoJvm(), protocolos);
            log.info("TLS hacia GestoPago con el truststore de la JVM, protocolos {}",
                    Arrays.toString(fabrica.getProtocolos()));
        }
        return fabrica;
    }

    @Bean
    public Client feignClient(TlsEstrictoSocketFactory gestoPagoSocketFactory) {
        return new Client.Default(gestoPagoSocketFactory, null);
    }
}
