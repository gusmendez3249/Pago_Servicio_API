package com.proyecto.servicios.controller;

import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.service.ProductosStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
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
@Tag(name = "Productos", description = "Endpoints para la consulta del catálogo de productos desde caché Redis o PostgreSQL")
public class ProductosController {

    private final ProductosStorageService storageService;

    public ProductosController(
            ProductosStorageService storageService) {

        this.storageService = storageService;
    }

    @GetMapping
    @Operation(summary = "Obtener lista de productos", description = "Obtiene los productos desde caché Redis (con fallback a PostgreSQL), opcionalmente filtrados por tipoFront.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Productos obtenidos exitosamente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetro tipoFront inválido", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "No se encontraron productos en el catálogo", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno al consultar el catálogo", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
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

            if (!productos.isEmpty()) {
                return ResponseEntity.ok(
                        GenericResponse.success(productos)
                );
            }
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                GenericResponse.error(
                        ErrorCode.NO_DATA.getCode(),
                        ErrorCode.NO_DATA.getMessage(),
                        Collections.emptyList()
                )
        );
    }

    @GetMapping("/agrupados")
    @Operation(summary = "Obtener productos agrupados", description = "Obtiene los productos agrupados por tipoFront.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Productos agrupados obtenidos exitosamente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "No se encontraron productos en el catálogo", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno al consultar el catálogo", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
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

            if (!agrupados.isEmpty()) {
                return ResponseEntity.ok(
                        GenericResponse.success(agrupados)
                );
            }
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
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