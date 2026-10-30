package com.proyecto.servicios.repositorys.productos;

import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductoPostgresRepository
        extends JpaRepository<ProductoPostgresEntity, Integer> {

    Optional<ProductoPostgresEntity> findFirstByIdServicioAndIdProducto(Integer idServicio, Integer idProducto);
}