package com.proyecto.servicios.service;

import com.proyecto.servicios.config.CatalogoProperties;
import com.proyecto.servicios.model.productos.ProductoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Completa al responder los campos calculados del catálogo (logoUrl, ayudaUrl, categoria).
 * No se persisten: dependen solo de la configuración y de los datos del producto.
 */
@Service
public class CatalogoPresentacionService {

    private final String baseImagenes;
    private final CatalogoProperties propiedades;

    public CatalogoPresentacionService(
            @Value("${gestopago.imagenes.base-url}") String baseImagenes,
            CatalogoProperties propiedades) {
        this.baseImagenes = quitarBarraFinal(baseImagenes);
        this.propiedades = propiedades;
    }

    public List<ProductoResponse> enriquecer(List<ProductoResponse> productos) {
        if (productos != null) {
            productos.forEach(this::enriquecer);
        }
        return productos;
    }

    public ProductoResponse enriquecer(ProductoResponse producto) {
        Integer idServicio = producto.getIdServicio();
        producto.setLogoUrl(logoUrl(idServicio));
        producto.setAyudaUrl(idServicio != null && Boolean.TRUE.equals(producto.getShowAyuda())
                ? baseImagenes + "/referencias/" + idServicio + ".jpg"
                : null);
        producto.setCategoria(categoriaDe(producto.getTipoFront()));
        return producto;
    }

    /** URL del logo del servicio; nula si no hay idServicio. */
    public String logoUrl(Integer idServicio) {
        return idServicio == null ? null : baseImagenes + "/servicios/" + idServicio + ".png";
    }

    private String categoriaDe(Integer tipoFront) {
        String nombre = tipoFront == null ? null : propiedades.getCategorias().get(tipoFront);
        return nombre != null ? nombre : propiedades.getCategoriaPorDefecto();
    }

    private static String quitarBarraFinal(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
