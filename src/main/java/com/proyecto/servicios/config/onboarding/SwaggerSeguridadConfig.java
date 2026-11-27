package com.proyecto.servicios.config.onboarding;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declara el esquema "bearerAuth" para que Swagger UI muestre el botón <b>Authorize</b>:
 * se pega el token obtenido en POST /api/v1/auth/login y Swagger lo envía en cada petición.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        description = "Token de sesión devuelto por POST /api/v1/auth/login (campo data.token). Pegue solo el token, sin la palabra Bearer."
)
public class SwaggerSeguridadConfig {
}
