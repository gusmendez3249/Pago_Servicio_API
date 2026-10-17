package com.proyecto.servicios.config.onboarding;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.proyecto.servicios.validation.TextoEstrictoDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Por defecto Jackson convierte un número o un booleano en texto ({@code "nombre": true} -> {@code "true"}).
 * En los DTO de entrada del onboarding todo campo de texto exige un valor JSON entre comillas: este módulo
 * aplica {@link TextoEstrictoDeserializer} a cada propiedad {@code String} de esas clases. Se limita al paquete
 * {@code dto.onboarding} para no alterar la deserialización de otros módulos (p. ej. respuestas de GestoPago).
 */
@Configuration
public class TextoEstrictoOnboardingConfig {

    private static final String PAQUETE_DTO = "com.proyecto.servicios.dto.onboarding";

    @Bean
    public Module textoEstrictoOnboardingModule() {
        SimpleModule modulo = new SimpleModule("TextoEstrictoOnboarding");
        modulo.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BeanDescription descripcion,
                                                         BeanDeserializerBuilder builder) {
                if (!descripcion.getBeanClass().getPackageName().equals(PAQUETE_DTO)) {
                    return builder;
                }
                List<SettableBeanProperty> textos = new ArrayList<>();
                builder.getProperties().forEachRemaining(p -> {
                    if (p.getType().hasRawClass(String.class) && !p.hasValueDeserializer()) {
                        textos.add(p);
                    }
                });
                textos.forEach(p -> builder.addOrReplaceProperty(p.withValueDeserializer(new TextoEstrictoDeserializer()), true));
                return builder;
            }
        });
        return modulo;
    }
}
