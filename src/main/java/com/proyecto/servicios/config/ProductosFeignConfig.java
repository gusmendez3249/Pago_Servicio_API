package com.proyecto.servicios.config;

import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class ProductosFeignConfig {

    private final long connectTimeout;
    private final long readTimeout;

    public ProductosFeignConfig(
            @Value("${gestopago.productos.connect-timeout:5000}") long connectTimeout,
            @Value("${gestopago.productos.read-timeout:10000}") long readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                true
        );
    }
}
