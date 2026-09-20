package com.proyecto.servicios.repositorys.productos;

import com.proyecto.servicios.entity.productos.ProductoRedis;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoRedisRepository
        extends CrudRepository<ProductoRedis, String> {
}