package com.proyecto.servicios.config;

import com.proyecto.servicios.service.onboarding.SesionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra la autenticación previa a la lectura del cuerpo (ver {@link AutenticacionPreviaInterceptor}). */
@Configuration
@RequiredArgsConstructor
public class AutenticacionPreviaConfig implements WebMvcConfigurer {

    private final SesionService sesionService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AutenticacionPreviaInterceptor(sesionService, false))
                .addPathPatterns(AutenticacionPreviaInterceptor.RUTAS_CON_SESION);
        registry.addInterceptor(new AutenticacionPreviaInterceptor(sesionService, true))
                .addPathPatterns(AutenticacionPreviaInterceptor.RUTAS_DE_EJECUTIVO);
    }
}
