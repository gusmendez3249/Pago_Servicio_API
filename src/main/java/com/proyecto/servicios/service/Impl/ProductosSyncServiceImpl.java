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

    public ProductosSyncServiceImpl(
            ProductosService productosService,
            ProductosStorageService storageService) {

        this.productosService = productosService;
        this.storageService = storageService;
    }

    @Override
    public void sincronizarProductos() {
        log.info("Inicio de sincronización de productos");

        List<ProductoResponse> productos =
                productosService.obtenerProductos();

        try {
            storageService.reemplazarEnRedis(productos);

            log.info(
                    "Productos guardados en Redis. Total={}",
                    productos.size()
            );

        } catch (Exception redisException) {
            log.error(
                    "Error al guardar productos en Redis"
            );

            storageService.reemplazarEnPostgres(productos);

            log.info(
                    "Productos guardados en PostgreSQL. Total={}",
                    productos.size()
            );
        }

        log.info("Fin de sincronización de productos");
    }
}

