package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.mapper.ProductoStorageMapper;
import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import com.proyecto.servicios.service.ProductosStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Almacenamiento del catálogo.
 * <ul>
 *   <li>Redis: el catálogo completo vive en UNA sola llave JSON ({@value #LLAVE}). Una lectura es un solo GET (antes eran
 *       ~900 HGETALL), y el reemplazo escribe en una llave temporal y la renombra: los lectores nunca ven el catálogo
 *       vacío ni a medias.</li>
 *   <li>PostgreSQL: borrado + inserción dentro de una transacción, por lo que también es atómico para los lectores.</li>
 * </ul>
 */
@Slf4j
@Service
public class ProductosStorageServiceImpl implements ProductosStorageService {

    static final String LLAVE = "catalogo:productos:v2";
    static final String LLAVE_TEMPORAL = LLAVE + ":tmp";
    private static final TypeReference<List<ProductoResponse>> TIPO_LISTA = new TypeReference<>() { };

    private final StringRedisTemplate redis;
    private final ProductoPostgresRepository postgresRepository;
    private final ProductoStorageMapper mapper;
    private final ObjectMapper json;

    public ProductosStorageServiceImpl(StringRedisTemplate redis, ProductoPostgresRepository postgresRepository,
                                       ProductoStorageMapper mapper, ObjectMapper json) {
        this.redis = redis;
        this.postgresRepository = postgresRepository;
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public void reemplazarEnRedis(List<ProductoResponse> productos) {
        redis.opsForValue().set(LLAVE_TEMPORAL, serializar(productos));
        redis.rename(LLAVE_TEMPORAL, LLAVE); // RENAME es atómico: reemplaza el catálogo de una sola vez
    }

    @Override
    @Transactional
    public void reemplazarEnPostgres(List<ProductoResponse> productos) {
        postgresRepository.deleteAllInBatch();
        List<ProductoPostgresEntity> entities = productos.stream().map(mapper::toPostgres).toList();
        postgresRepository.saveAll(entities);
    }

    @Override
    public List<ProductoResponse> consultarRedis() {
        String valor = redis.opsForValue().get(LLAVE);
        if (valor == null || valor.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(valor, TIPO_LISTA);
        } catch (JsonProcessingException exception) {
            log.error("El catálogo guardado en Redis no es un JSON válido; se ignora");
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponse> consultarPostgres() {
        return postgresRepository.findAll().stream().map(mapper::fromPostgres).toList();
    }

    private String serializar(List<ProductoResponse> productos) {
        try {
            return json.writeValueAsString(productos);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el catálogo", exception);
        }
    }
}
