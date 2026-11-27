package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.service.ProductosService;
import com.proyecto.servicios.service.ProductosStorageService;
import com.proyecto.servicios.service.ProductosSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ProductosSyncServiceImpl
        implements ProductosSyncService {

    private final ProductosService productosService;
    private final ProductosStorageService storageService;
    private final com.proyecto.servicios.service.CatalogoCacheService catalogoCache;

    public ProductosSyncServiceImpl(
            ProductosService productosService,
            ProductosStorageService storageService,
            com.proyecto.servicios.service.CatalogoCacheService catalogoCache) {

        this.productosService = productosService;
        this.storageService = storageService;
        this.catalogoCache = catalogoCache;
    }

    @Override
    public void sincronizarProductos() {
        log.info("Inicio de sincronización de productos");

        List<ProductoResponse> productos =
                productosService.obtenerProductos();

        // PostgreSQL es la fuente que valida los pagos: siempre se actualiza (antes solo se escribía si Redis fallaba)
        storageService.reemplazarEnPostgres(productos);
        log.info("Productos guardados en PostgreSQL. Total={}", productos.size());

        try {
            storageService.reemplazarEnRedis(productos);
            log.info("Productos guardados en Redis. Total={}", productos.size());
        } catch (Exception redisException) {
            // Redis es solo caché: el catálogo se sigue sirviendo desde PostgreSQL
            log.error("Error al guardar productos en Redis. causa={}", redisException.getClass().getSimpleName());
        }
        catalogoCache.invalidar();

        log.info("Fin de sincronización de productos");
    }
}
