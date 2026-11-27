package com.proyecto.servicios.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Presentación del catálogo: Gesto Pago no envía nombres de categoría, por eso el mapa
 * tipoFront -> nombre legible es configurable ({@code app.catalogo.*}).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.catalogo")
public class CatalogoProperties {

    private Map<Integer, String> categorias = new HashMap<>();
    private String categoriaPorDefecto = "Otros servicios";
}
