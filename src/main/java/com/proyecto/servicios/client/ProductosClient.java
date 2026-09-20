package com.proyecto.servicios.client;

import com.proyecto.servicios.config.ProductosFeignConfig;
import com.proyecto.servicios.model.productos.ProductosXmlResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "productosClient",
        url = "${gestopago.base-url}",
        configuration = ProductosFeignConfig.class
)
public interface ProductosClient {

    @GetMapping(
            value = "${gestopago.productos.endpoint}",
            produces = MediaType.APPLICATION_XML_VALUE
    )
    ProductosXmlResponse obtenerProductos(
            @RequestHeader("Authorization") String authorization
    );
}