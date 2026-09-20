package com.proyecto.servicios.repositorys.productos;

import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoPostgresRepository
        extends JpaRepository<ProductoPostgresEntity, Integer> {
}