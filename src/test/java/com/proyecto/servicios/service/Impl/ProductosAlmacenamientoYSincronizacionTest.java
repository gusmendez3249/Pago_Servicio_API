package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.SendTxClient;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.mapper.ProductoStorageMapper;
import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import com.proyecto.servicios.service.CatalogoCacheService;
import com.proyecto.servicios.service.CifradoService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.ProductosService;
import com.proyecto.servicios.service.ProductosStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Almacenamiento del catálogo (una sola llave en Redis + PostgreSQL), su sincronización y la consulta de estado. */
@ExtendWith(MockitoExtension.class)
class ProductosAlmacenamientoYSincronizacionTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valores;
    @Mock private ProductoPostgresRepository postgresRepository;
    @Mock private ProductoStorageMapper mapper;
    @Mock private ProductosService productosService;
    @Mock private ProductosStorageService storageService;
    @Mock private CatalogoCacheService catalogoCache;

    private final ObjectMapper json = new ObjectMapper();
    private final ProductoResponse producto = new ProductoResponse();

    private ProductosStorageServiceImpl storage() {
        return new ProductosStorageServiceImpl(redis, postgresRepository, mapper, json);
    }

    @Test
    void reemplazarEnRedisEscribeEnTemporalYRenombraDeFormaAtomica() throws Exception {
        when(redis.opsForValue()).thenReturn(valores);
        producto.setServicio("Telcel");
        producto.setPrecio(new BigDecimal("15.00"));

        storage().reemplazarEnRedis(List.of(producto));

        ArgumentCaptor<String> guardado = ArgumentCaptor.forClass(String.class);
        InOrder orden = inOrder(valores, redis);
        orden.verify(valores).set(eq(ProductosStorageServiceImpl.LLAVE_TEMPORAL), guardado.capture());
        orden.verify(redis).rename(ProductosStorageServiceImpl.LLAVE_TEMPORAL, ProductosStorageServiceImpl.LLAVE);
        assertTrue(guardado.getValue().contains("Telcel"));
    }

    @Test
    void consultarRedisLeeUnaSolaLlave() throws Exception {
        producto.setServicio("CFE");
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.get(ProductosStorageServiceImpl.LLAVE)).thenReturn(json.writeValueAsString(List.of(producto)));

        List<ProductoResponse> leidos = storage().consultarRedis();

        assertEquals(1, leidos.size());
        assertEquals("CFE", leidos.get(0).getServicio());
    }

    @Test
    void consultarRedisSinLlaveOConJsonInvalidoDevuelveVacio() {
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.get(ProductosStorageServiceImpl.LLAVE)).thenReturn(null, "{no-json");

        assertTrue(storage().consultarRedis().isEmpty());
        assertTrue(storage().consultarRedis().isEmpty());
    }

    @Test
    void reemplazarEnPostgresBorraAntesDeGuardar() {
        ProductoPostgresEntity entidad = new ProductoPostgresEntity();
        when(mapper.toPostgres(producto)).thenReturn(entidad);

        storage().reemplazarEnPostgres(List.of(producto));

        InOrder orden = inOrder(postgresRepository);
        orden.verify(postgresRepository).deleteAllInBatch();
        orden.verify(postgresRepository).saveAll(List.of(entidad));
    }

    @Test
    void consultarPostgresConvierteCadaRegistro() {
        ProductoPostgresEntity entidad = new ProductoPostgresEntity();
        when(postgresRepository.findAll()).thenReturn(List.of(entidad));
        when(mapper.fromPostgres(entidad)).thenReturn(producto);

        assertEquals(List.of(producto), storage().consultarPostgres());
    }

    @Test
    void sincronizacionActualizaPostgresYRedisEInvalidaLaCache() {
        when(productosService.obtenerProductos()).thenReturn(List.of(producto));

        new ProductosSyncServiceImpl(productosService, storageService, catalogoCache).sincronizarProductos();

        verify(storageService).reemplazarEnPostgres(List.of(producto));
        verify(storageService).reemplazarEnRedis(List.of(producto));
        verify(catalogoCache).invalidar();
    }

    @Test
    void sincronizacionSigueSiRedisFalla() {
        when(productosService.obtenerProductos()).thenReturn(List.of(producto));
        doThrow(new RedisConnectionFailureException("sin redis")).when(storageService).reemplazarEnRedis(anyList());

        new ProductosSyncServiceImpl(productosService, storageService, catalogoCache).sincronizarProductos();

        verify(storageService).reemplazarEnPostgres(List.of(producto));
        verify(catalogoCache).invalidar();
    }

    @Test
    void consultaDeEstadoSinParametrosGuardadosNoInventaResultados() {
        ConsultaEstadoPagoServiceImpl consulta = new ConsultaEstadoPagoServiceImpl(mock(SendTxClient.class),
                mock(GestoPagoTokenService.class), mock(CifradoService.class), 1, "DEV", "", 5000, 15000);

        assertTrue(consulta.consultar(new TransaccionPagoEntity()).isEmpty());
    }
}
