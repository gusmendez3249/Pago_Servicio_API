package com.proyecto.servicios.controller;

import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.service.CatalogoPresentacionService;
import com.proyecto.servicios.service.CatalogoCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/productos")
@Tag(name = "Productos", description = "Endpoints para la consulta del catálogo de productos desde caché Redis o PostgreSQL")
public class ProductosController {

    /** El catálogo es público y cambia una vez al día: navegadores y CDN pueden reutilizarlo 5 minutos. */
    private static final CacheControl CACHE_CATALOGO = CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

    private final CatalogoCacheService catalogo;

    private final CatalogoPresentacionService presentacionService;

    public ProductosController(
            CatalogoCacheService catalogo,
            CatalogoPresentacionService presentacionService) {

        this.catalogo = catalogo;
        this.presentacionService = presentacionService;
    }

    @GetMapping
    @Operation(summary = "Obtener lista de productos", description = "Obtiene los productos desde caché Redis (con fallback a PostgreSQL), opcionalmente filtrados por tipoFront. Cada producto incluye los campos calculados logoUrl (imagen del servicio), ayudaUrl (imagen de ayuda de la referencia, solo si showAyuda es true) y categoria (nombre legible según tipoFront).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Productos obtenidos exitosamente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetro tipoFront inválido", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "No se encontraron productos en el catálogo", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno al consultar el catálogo", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<List<ProductoResponse>>>
    obtenerProductos(
            @RequestParam(name = "tipoFront", required = false) Integer tipoFront) {

        List<ProductoResponse> productos = catalogo.productos();

        if (productos != null && !productos.isEmpty()) {
            if (tipoFront != null) {
                productos = productos.stream()
                        .filter(p -> tipoFront.equals(p.getTipoFront()))
                        .toList();
            }

            if (!productos.isEmpty()) {
                return ResponseEntity.ok().cacheControl(CACHE_CATALOGO)
                        .body(GenericResponse.success(productos));
            }
        }

        // GlobalExceptionHandler responde 404 con el código y mensaje de NO_DATA
        throw new IntegrationException(ErrorCode.NO_DATA);
    }

    @GetMapping("/agrupados")
    @Operation(summary = "Obtener productos agrupados", description = "Obtiene los productos agrupados por tipoFront. Cada producto incluye los campos calculados logoUrl, ayudaUrl (solo si showAyuda es true) y categoria.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Productos agrupados obtenidos exitosamente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "No se encontraron productos en el catálogo", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno al consultar el catálogo", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<Map<Integer, List<ProductoResponse>>>>
    obtenerProductosAgrupados() {

        List<ProductoResponse> productos = catalogo.productos();

        if (productos != null && !productos.isEmpty()) {
            Map<Integer, List<ProductoResponse>> agrupados = productos.stream()
                    .filter(p -> p.getTipoFront() != null)
                    .collect(Collectors.groupingBy(
                            ProductoResponse::getTipoFront,
                            TreeMap::new,
                            Collectors.toList()
                    ));

            if (!agrupados.isEmpty()) {
                return ResponseEntity.ok().cacheControl(CACHE_CATALOGO)
                        .body(GenericResponse.success(agrupados));
            }
        }

        // GlobalExceptionHandler responde 404 con el código y mensaje de NO_DATA
        throw new IntegrationException(ErrorCode.NO_DATA);
    }
}
