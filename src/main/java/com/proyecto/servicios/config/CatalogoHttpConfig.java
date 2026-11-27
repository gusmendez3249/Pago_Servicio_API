package com.proyecto.servicios.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * ETag en el catálogo público: un cliente que ya tiene la versión vigente recibe 304 sin cuerpo en lugar de 580 KB.
 * Es débil para que la compresión gzip siga aplicando.
 */
@Configuration
public class CatalogoHttpConfig {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagCatalogo() {
        ShallowEtagHeaderFilter filtro = new ShallowEtagHeaderFilter();
        // ETag débil (W/"..."): Tomcat no comprime respuestas con ETag fuerte (noCompressionStrongETag)
        filtro.setWriteWeakETag(true);
        FilterRegistrationBean<ShallowEtagHeaderFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.addUrlPatterns("/productos", "/productos/*");
        return registro;
    }
}
