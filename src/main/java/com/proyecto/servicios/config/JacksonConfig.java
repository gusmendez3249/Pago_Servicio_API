package com.proyecto.servicios.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer disableCoercionCustomizer() {
        return builder -> {
            builder.featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            builder.postConfigurer(objectMapper -> {
                // Desactivar la conversión/coerción automática de String ("1") a números enteros (1)
                objectMapper.coercionConfigFor(LogicalType.Integer)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail);

                objectMapper.coercionConfigFor(LogicalType.Float)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail);

                objectMapper.coercionConfigFor(LogicalType.Boolean)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
            });
        };
    }
}
