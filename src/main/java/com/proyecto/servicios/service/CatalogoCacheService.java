package com.proyecto.servicios.service;

import com.proyecto.servicios.model.productos.ProductoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Catálogo en memoria para {@code GET /productos}: el catálogo cambia una vez al día, así que no se consulta Redis ni
 * PostgreSQL en cada petición.
 * <ul>
 *   <li>La copia se recarga al vencer {@code app.catalogo.cache-ttl-segundos} o cuando la sincronización la invalida.</li>
 *   <li>Solo un hilo recarga; los demás siguen sirviendo la copia anterior (sin estampida contra Redis).</li>
 *   <li>Circuit breaker: si Redis falla, durante {@code app.catalogo.redis-pausa-segundos} se va directo a PostgreSQL,
 *       sin volver a esperar el timeout de Redis en cada petición.</li>
 *   <li>Si Redis y PostgreSQL fallan, se sigue sirviendo la última copia buena.</li>
 * </ul>
 */
@Slf4j
@Service
public class CatalogoCacheService {

    private record Copia(List<ProductoResponse> productos, Instant cargada) {
    }

    private final ProductosStorageService storage;
    private final CatalogoPresentacionService presentacion;
    private final Duration ttl;
    private final Duration pausaRedis;
    private final Clock reloj;

    private volatile Copia copia;
    private volatile Instant redisPausadoHasta = Instant.MIN;

    @Autowired
    public CatalogoCacheService(ProductosStorageService storage, CatalogoPresentacionService presentacion,
                                @Value("${app.catalogo.cache-ttl-segundos:600}") long ttlSegundos,
                                @Value("${app.catalogo.redis-pausa-segundos:30}") long pausaRedisSegundos) {
        this(storage, presentacion, ttlSegundos, pausaRedisSegundos, Clock.systemUTC());
    }

    CatalogoCacheService(ProductosStorageService storage, CatalogoPresentacionService presentacion,
                         long ttlSegundos, long pausaRedisSegundos, Clock reloj) {
        this.storage = storage;
        this.presentacion = presentacion;
        this.ttl = Duration.ofSeconds(ttlSegundos);
        this.pausaRedis = Duration.ofSeconds(pausaRedisSegundos);
        this.reloj = reloj;
    }

    /** Catálogo enriquecido (logo, ayuda, categoría). Lista inmutable compartida: no se debe modificar. */
    public List<ProductoResponse> productos() {
        Copia actual = copia;
        if (actual != null && !vencida(actual)) {
            return actual.productos();
        }
        return recargar(actual);
    }

    /** La sincronización diaria llama aquí para que la siguiente petición lea el catálogo nuevo. */
    public void invalidar() {
        copia = null;
    }

    private synchronized List<ProductoResponse> recargar(Copia anterior) {
        Copia actual = copia;
        if (actual != null && actual != anterior && !vencida(actual)) {
            return actual.productos(); // otro hilo ya recargó
        }
        try {
            List<ProductoResponse> productos = List.copyOf(presentacion.enriquecer(new java.util.ArrayList<>(leer())));
            if (!productos.isEmpty()) {
                copia = new Copia(productos, reloj.instant());
            }
            return productos;
        } catch (RuntimeException exception) {
            if (actual != null) {
                log.error("No se pudo recargar el catálogo; se sirve la copia anterior. causa={}",
                        exception.getClass().getSimpleName());
                return actual.productos();
            }
            throw exception;
        }
    }

    private List<ProductoResponse> leer() {
        if (reloj.instant().isAfter(redisPausadoHasta)) {
            try {
                List<ProductoResponse> desdeRedis = storage.consultarRedis();
                if (desdeRedis != null && !desdeRedis.isEmpty()) {
                    return desdeRedis;
                }
            } catch (RuntimeException exception) {
                redisPausadoHasta = reloj.instant().plus(pausaRedis);
                log.warn("Redis no respondió al leer el catálogo; se usa PostgreSQL durante {} s. causa={}",
                        pausaRedis.toSeconds(), exception.getClass().getSimpleName());
            }
        }
        return storage.consultarPostgres();
    }

    private boolean vencida(Copia c) {
        return c.cargada().plus(ttl).isBefore(reloj.instant());
    }
}
