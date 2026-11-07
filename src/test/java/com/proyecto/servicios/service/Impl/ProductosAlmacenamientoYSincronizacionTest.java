package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.entity.productos.ProductoRedis;
import com.proyecto.servicios.mapper.ProductoStorageMapper;
import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import com.proyecto.servicios.repositorys.productos.ProductoRedisRepository;
import com.proyecto.servicios.service.ProductosService;
import com.proyecto.servicios.service.ProductosStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Almacenamiento del catálogo de productos (Redis con respaldo en PostgreSQL), su sincronización y la consulta de estado. */
@ExtendWith(MockitoExtension.class)
class ProductosAlmacenamientoYSincronizacionTest {

    @Mock private ProductoRedisRepository redisRepository;
    @Mock private ProductoPostgresRepository postgresRepository;
    @Mock private ProductoStorageMapper mapper;
    @Mock private ProductosService productosService;
    @Mock private ProductosStorageService storageService;

    private final ProductoResponse producto = new ProductoResponse();

    private ProductosStorageServiceImpl storage() {
        return new ProductosStorageServiceImpl(redisRepository, postgresRepository, mapper);
    }

    @Test
    void reemplazarEnRedisBorraAntesDeGuardar() {
        ProductoRedis redis = new ProductoRedis();
        when(mapper.toRedis(producto)).thenReturn(redis);

        storage().reemplazarEnRedis(List.of(producto));

        InOrder orden = inOrder(redisRepository);
        orden.verify(redisRepository).deleteAll();
        orden.verify(redisRepository).saveAll(List.of(redis));
    }

    @Test
    void reemplazarEnPostgresBorraAntesDeGuardar() {
        ProductoPostgresEntity entidad = new ProductoPostgresEntity();
        when(mapper.toPostgres(producto)).thenReturn(entidad);

        storage().reemplazarEnPostgres(List.of(producto));

        InOrder orden = inOrder(postgresRepository);
        orden.verify(postgresRepository).deleteAll();
        orden.verify(postgresRepository).saveAll(List.of(entidad));
    }

    @Test
    void consultasConviertenCadaRegistro() {
        ProductoRedis redis = new ProductoRedis();
        ProductoPostgresEntity entidad = new ProductoPostgresEntity();
        when(redisRepository.findAll()).thenReturn(List.of(redis));
        when(postgresRepository.findAll()).thenReturn(List.of(entidad));
        when(mapper.fromRedis(redis)).thenReturn(producto);
        when(mapper.fromPostgres(entidad)).thenReturn(producto);

        assertEquals(List.of(producto), storage().consultarRedis());
        assertEquals(List.of(producto), storage().consultarPostgres());
    }

    @Test
    void sincronizacionGuardaEnRedisCuandoEstaDisponible() {
        when(productosService.obtenerProductos()).thenReturn(List.of(producto));

        new ProductosSyncServiceImpl(productosService, storageService).sincronizarProductos();

        verify(storageService).reemplazarEnRedis(List.of(producto));
        verify(storageService, never()).reemplazarEnPostgres(anyList());
    }

    @Test
    void sincronizacionUsaPostgresSiRedisFalla() {
        when(productosService.obtenerProductos()).thenReturn(List.of(producto));
        doThrow(new RedisConnectionFailureException("sin redis")).when(storageService).reemplazarEnRedis(anyList());

        new ProductosSyncServiceImpl(productosService, storageService).sincronizarProductos();

        verify(storageService).reemplazarEnPostgres(List.of(producto));
    }

    @Test
    void consultaDeEstadoNoInventaResultados() {
        assertTrue(new ConsultaEstadoPagoServiceImpl().consultar("K3J9X0A7ZQ").isEmpty());
    }
}
