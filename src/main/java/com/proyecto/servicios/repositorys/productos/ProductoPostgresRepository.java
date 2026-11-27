package com.proyecto.servicios.repositorys.productos;

import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoPostgresRepository
        extends JpaRepository<ProductoPostgresEntity, Integer> {

    Optional<ProductoPostgresEntity> findFirstByIdServicioAndIdProducto(Integer idServicio, Integer idProducto);

    /** Todos los productos de los servicios indicados (una sola consulta; el emparejado por producto es en memoria). */
    List<ProductoPostgresEntity> findByIdServicioIn(Collection<Integer> idsServicio);

    /** Producto de consulta "hermano" de un servicio: el de menor idProducto entre los tipoFront indicados. */
    Optional<ProductoPostgresEntity> findFirstByIdServicioAndTipoFrontInOrderByIdProductoAsc(
            Integer idServicio, Collection<Integer> tiposFront);
}