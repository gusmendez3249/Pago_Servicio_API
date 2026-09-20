package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.entity.productos.ProductoRedis;
import com.proyecto.servicios.mapper.ProductoStorageMapper;
import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import com.proyecto.servicios.repositorys.productos.ProductoRedisRepository;
import com.proyecto.servicios.service.ProductosStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class ProductosStorageServiceImpl
        implements ProductosStorageService {

    private final ProductoRedisRepository redisRepository;
    private final ProductoPostgresRepository postgresRepository;
    private final ProductoStorageMapper mapper;

    public ProductosStorageServiceImpl(
            ProductoRedisRepository redisRepository,
            ProductoPostgresRepository postgresRepository,
            ProductoStorageMapper mapper) {

        this.redisRepository = redisRepository;
        this.postgresRepository = postgresRepository;
        this.mapper = mapper;
    }

    @Override
    public void reemplazarEnRedis(
            List<ProductoResponse> productos) {

        redisRepository.deleteAll();

        List<ProductoRedis> entities = productos.stream()
                .map(mapper::toRedis)
                .toList();

        redisRepository.saveAll(entities);
    }

    @Override
    public void reemplazarEnPostgres(
            List<ProductoResponse> productos) {

        postgresRepository.deleteAll();

        List<ProductoPostgresEntity> entities = productos.stream()
                .map(mapper::toPostgres)
                .toList();

        postgresRepository.saveAll(entities);
    }

    @Override
    public List<ProductoResponse> consultarRedis() {
        return StreamSupport
                .stream(
                        redisRepository.findAll().spliterator(),
                        false
                )
                .map(mapper::fromRedis)
                .toList();
    }

    @Override
    public List<ProductoResponse> consultarPostgres() {
        return postgresRepository.findAll()
                .stream()
                .map(mapper::fromPostgres)
                .toList();
    }
}