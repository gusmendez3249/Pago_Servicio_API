package com.proyecto.servicios.service;

import com.proyecto.servicios.config.CatalogoProperties;
import com.proyecto.servicios.model.productos.ProductoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogoCacheServiceTest {

    private final ProductosStorageService storage = mock(ProductosStorageService.class);
    private final CatalogoPresentacionService presentacion = new CatalogoPresentacionService("http://img", new CatalogoProperties());
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-26T10:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora.get(); }
    };
    private final CatalogoCacheService cache = new CatalogoCacheService(storage, presentacion, 600, 30, reloj);

    private static ProductoResponse producto(String servicio) {
        ProductoResponse p = new ProductoResponse();
        p.setServicio(servicio);
        p.setIdServicio(1);
        p.setTipoFront(1);
        return p;
    }

    @Test
    void sirveDesdeMemoriaSinVolverAConsultarHastaQueVence() {
        when(storage.consultarRedis()).thenReturn(List.of(producto("Telcel")));

        cache.productos();
        cache.productos();
        verify(storage, times(1)).consultarRedis();
        assertEquals("http://img/servicios/1.png", cache.productos().get(0).getLogoUrl(), "se enriquece una sola vez");

        ahora.set(ahora.get().plus(Duration.ofMinutes(11)));
        cache.productos();
        verify(storage, times(2)).consultarRedis();
    }

    @Test
    void conRedisCaidoUsaPostgresYNoReintentaRedisDuranteLaPausa() {
        when(storage.consultarRedis()).thenThrow(new RedisConnectionFailureException("caido"));
        when(storage.consultarPostgres()).thenReturn(List.of(producto("CFE")));

        assertEquals("CFE", cache.productos().get(0).getServicio());
        cache.invalidar();
        cache.productos();
        verify(storage, times(1)).consultarRedis(); // circuit breaker abierto: no se espera a Redis otra vez

        ahora.set(ahora.get().plusSeconds(31));
        cache.invalidar();
        cache.productos();
        verify(storage, times(2)).consultarRedis();
    }

    @Test
    void siTodoFallaSirveLaUltimaCopiaBuena() {
        when(storage.consultarRedis()).thenReturn(List.of(producto("Izzi")));
        cache.productos();

        ahora.set(ahora.get().plus(Duration.ofMinutes(11)));
        when(storage.consultarRedis()).thenThrow(new RedisConnectionFailureException("caido"));
        when(storage.consultarPostgres()).thenThrow(new IllegalStateException("sin bd"));

        assertEquals("Izzi", cache.productos().get(0).getServicio());
    }

    @Test
    void redisVacioCaeAPostgres() {
        when(storage.consultarRedis()).thenReturn(List.of());
        when(storage.consultarPostgres()).thenReturn(List.of(producto("Agua")));

        assertEquals("Agua", cache.productos().get(0).getServicio());
    }
}
