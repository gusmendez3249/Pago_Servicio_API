package com.proyecto.servicios.service;

import com.proyecto.servicios.config.CatalogoProperties;
import com.proyecto.servicios.model.productos.ProductoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoPresentacionServiceTest {

    private static final String BASE = "https://host/sistema/images/gestopago";

    private CatalogoPresentacionService servicio;

    @BeforeEach
    void preparar() {
        CatalogoProperties props = new CatalogoProperties();
        props.setCategorias(Map.of(1, "Recargas y tarjetas de regalo", 40, "Envíos de dinero"));
        props.setCategoriaPorDefecto("Otros servicios");
        servicio = new CatalogoPresentacionService(BASE + "/", props);
    }

    private static ProductoResponse producto(Integer idServicio, Integer tipoFront, Boolean showAyuda) {
        ProductoResponse p = new ProductoResponse();
        p.setIdServicio(idServicio);
        p.setTipoFront(tipoFront);
        p.setShowAyuda(showAyuda);
        return p;
    }

    @Test
    void logoUrlSeArmaConElIdServicio() {
        ProductoResponse p = servicio.enriquecer(producto(7, 1, false));
        assertThat(p.getLogoUrl()).isEqualTo(BASE + "/servicios/7.png");
    }

    @Test
    void ayudaUrlSoloConShowAyudaVerdadero() {
        assertThat(servicio.enriquecer(producto(7, 1, true)).getAyudaUrl())
                .isEqualTo(BASE + "/referencias/7.jpg");
        assertThat(servicio.enriquecer(producto(7, 1, false)).getAyudaUrl()).isNull();
        assertThat(servicio.enriquecer(producto(7, 1, null)).getAyudaUrl()).isNull();
    }

    @Test
    void sinIdServicioNoHayUrls() {
        ProductoResponse p = servicio.enriquecer(producto(null, 1, true));
        assertThat(p.getLogoUrl()).isNull();
        assertThat(p.getAyudaUrl()).isNull();
    }

    @Test
    void categoriaConocidaDesconocidaYNula() {
        assertThat(servicio.enriquecer(producto(1, 1, false)).getCategoria())
                .isEqualTo("Recargas y tarjetas de regalo");
        assertThat(servicio.enriquecer(producto(1, 99, false)).getCategoria()).isEqualTo("Otros servicios");
        assertThat(servicio.enriquecer(producto(1, null, false)).getCategoria()).isEqualTo("Otros servicios");
    }

    @Test
    void enriquecerListaAceptaNulo() {
        assertThat(servicio.enriquecer((List<ProductoResponse>) null)).isNull();
        List<ProductoResponse> lista = servicio.enriquecer(List.of(producto(1, 40, false)));
        assertThat(lista.get(0).getCategoria()).isEqualTo("Envíos de dinero");
    }

    @Configuration
    @EnableConfigurationProperties(CatalogoProperties.class)
    static class Config {
    }

    @Test
    void applicationPropertiesPreservaAcentosYUrlBase() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class)
                .run((AssertableApplicationContext ctx) -> {
                    CatalogoProperties props = ctx.getBean(CatalogoProperties.class);
                    assertThat(props.getCategorias().get(40)).isEqualTo("Envíos de dinero");
                    assertThat(props.getCategorias()).containsKeys(1, 2, 4, 40);
                    assertThat(props.getCategoriaPorDefecto()).isEqualTo("Otros servicios");
                    assertThat(ctx.getEnvironment().getProperty("gestopago.imagenes.base-url"))
                            .isEqualTo("https://gestopago.portalventas.net/sistema/images/gestopago");
                });
    }
}
