package com.proyecto.servicios.service;

import com.proyecto.servicios.model.productos.ProductoResponse;

import java.util.List;

public interface ProductosStorageService {

    void reemplazarEnRedis(List<ProductoResponse> productos);

    void reemplazarEnPostgres(List<ProductoResponse> productos);

    List<ProductoResponse> consultarRedis();

    List<ProductoResponse> consultarPostgres();
}