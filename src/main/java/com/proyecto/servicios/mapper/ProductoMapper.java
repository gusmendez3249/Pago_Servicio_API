package com.proyecto.servicios.mapper;

import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.model.productos.ProductoXml;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    @Mapping(target = "logoUrl", ignore = true)
    @Mapping(target = "ayudaUrl", ignore = true)
    @Mapping(target = "categoria", ignore = true)
    ProductoResponse toResponse(ProductoXml productoXml);
}