package com.proyecto.servicios.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS para que la SPA (otro origen) pueda llamar al API. Se registra con la mayor precedencia para correr antes
 * que {@link CabecerasSeguridadFilter}, {@link RequestSizeLimitFilter} y {@link LoginRateLimitFilter}: el
 * preflight {@code OPTIONS} se responde aquí mismo, sin token y sin pasar por el resto de la cadena.
 * Los orígenes son una lista exacta ({@code app.cors.allowed-origins}); nunca se usa {@code *}.
 */
@Configuration
public class CorsConfig {

    static final List<String> METODOS = List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.OPTIONS.name());
    static final List<String> HEADERS_PERMITIDOS = List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "Idempotency-Key");
    static final List<String> HEADERS_EXPUESTOS = List.of(HttpHeaders.RETRY_AFTER, HttpHeaders.WWW_AUTHENTICATE);
    static final long MAX_AGE_SEGUNDOS = 600;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String origenesPermitidos) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(parsearOrigenes(origenesPermitidos));
        configuracion.setAllowedMethods(METODOS);
        configuracion.setAllowedHeaders(HEADERS_PERMITIDOS);
        configuracion.setExposedHeaders(HEADERS_EXPUESTOS);
        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(MAX_AGE_SEGUNDOS);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);

        FilterRegistrationBean<CorsFilter> registro = new FilterRegistrationBean<>(new CorsFilter(fuente));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }

    /** Separa por comas y recorta espacios; descarta vacíos y el comodín {@code *}. */
    static List<String> parsearOrigenes(String valor) {
        return Arrays.stream(valor.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty() && !"*".equals(origen))
                .toList();
    }
}
