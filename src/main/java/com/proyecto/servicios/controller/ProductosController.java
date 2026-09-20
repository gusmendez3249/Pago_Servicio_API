package com.proyecto.servicios.controller;

import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.service.ProductosStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/productos")
public class ProductosController {

    private final ProductosStorageService storageService;

    public ProductosController(
            ProductosStorageService storageService) {

        this.storageService = storageService;
    }

    @GetMapping
    public ResponseEntity<GenericResponse<List<ProductoResponse>>>
    obtenerProductos(
            @RequestParam(name = "tipoFront", required = false) Integer tipoFront) {

        List<ProductoResponse> productos = obtenerProductosDesdeStorage();

        if (productos != null && !productos.isEmpty()) {
            if (tipoFront != null) {
                productos = productos.stream()
                        .filter(p -> tipoFront.equals(p.getTipoFront()))
                        .toList();
            }

            return ResponseEntity.ok(
                    GenericResponse.success(productos)
            );
        }

        return ResponseEntity.ok(
                GenericResponse.error(
                        ErrorCode.NO_DATA.getCode(),
                        ErrorCode.NO_DATA.getMessage(),
                        Collections.emptyList()
                )
        );
    }

    @GetMapping("/agrupados")
    public ResponseEntity<GenericResponse<Map<Integer, List<ProductoResponse>>>>
    obtenerProductosAgrupados() {

        List<ProductoResponse> productos = obtenerProductosDesdeStorage();

        if (productos != null && !productos.isEmpty()) {
            Map<Integer, List<ProductoResponse>> agrupados = productos.stream()
                    .filter(p -> p.getTipoFront() != null)
                    .collect(Collectors.groupingBy(
                            ProductoResponse::getTipoFront,
                            TreeMap::new,
                            Collectors.toList()
                    ));

            return ResponseEntity.ok(
                    GenericResponse.success(agrupados)
            );
        }

        return ResponseEntity.ok(
                GenericResponse.error(
                        ErrorCode.NO_DATA.getCode(),
                        ErrorCode.NO_DATA.getMessage(),
                        Collections.emptyMap()
                )
        );
    }

    private List<ProductoResponse> obtenerProductosDesdeStorage() {
        List<ProductoResponse> productos;

        try {
            productos = storageService.consultarRedis();
            if (productos != null && !productos.isEmpty()) {
                return productos;
            }
        } catch (Exception redisException) {
            // Fallback a Postgres
        }

        return consultarPostgres();
    }

    private List<ProductoResponse> consultarPostgres() {
        try {
            return storageService.consultarPostgres();

        } catch (Exception postgresException) {
            return Collections.emptyList();
        }
    }
}