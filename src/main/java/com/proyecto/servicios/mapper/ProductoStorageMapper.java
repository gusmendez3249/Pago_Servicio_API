package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.entity.productos.ProductoRedis;
import com.proyecto.servicios.model.productos.ProductoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductoStorageMapper {

    @Mapping(target = "id", ignore = true)
    ProductoRedis toRedis(ProductoResponse source);

    @Mapping(target = "id", ignore = true)
    ProductoPostgresEntity toPostgres(ProductoResponse source);

    ProductoResponse fromRedis(ProductoRedis source);

    ProductoResponse fromPostgres(
            ProductoPostgresEntity source
    );
}