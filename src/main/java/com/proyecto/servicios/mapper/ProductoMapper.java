package com.proyecto.servicios.mapper;

import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.model.productos.ProductoXml;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    ProductoResponse toResponse(ProductoXml productoXml);
}