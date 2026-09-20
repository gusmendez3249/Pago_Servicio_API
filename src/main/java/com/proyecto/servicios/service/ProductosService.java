package com.proyecto.servicios.service;

import com.proyecto.servicios.model.productos.ProductoResponse;

import java.util.List;

public interface ProductosService {

    List<ProductoResponse> obtenerProductos();
}