package com.proyecto.servicios.scheduler;

import com.proyecto.servicios.service.ProductosSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductosSyncScheduler {

    private final ProductosSyncService syncService;

    public ProductosSyncScheduler(
            ProductosSyncService syncService) {

        this.syncService = syncService;
    }

    @Scheduled(
            cron = "${gestopago.productos.sync-cron}",
            zone = "${gestopago.productos.sync-zone}"
    )
    public void ejecutarSincronizacion() {
        log.info("Ejecutando sincronización diaria");
        syncService.sincronizarProductos();
    }
}