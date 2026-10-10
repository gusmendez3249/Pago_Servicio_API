package com.proyecto.servicios.controller.onboarding;

import com.proyecto.servicios.entity.onboarding.CatNacionalidadEntity;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.repositorys.onboarding.CatNacionalidadRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cat")
@RequiredArgsConstructor
@Tag(name = "Catálogo Nacionalidades", description = "Endpoints para consultar el catálogo de nacionalidades registrado en la base de datos")
public class CatNacionalidadController {

    private final CatNacionalidadRepository catNacionalidadRepository;

    @GetMapping("/nacionalidades")
    @Operation(summary = "Obtener catálogo de nacionalidades activas de la BD", description = "Retorna la lista de nacionalidades registradas en la tabla cat_nacionalidad.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Catálogo de nacionalidades obtenido exitosamente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error: Error interno al consultar el catálogo en la base de datos", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<List<CatNacionalidadEntity>>> obtenerNacionalidades() {
        List<CatNacionalidadEntity> lista = catNacionalidadRepository.findByActivoTrue();
        return ResponseEntity.ok(GenericResponse.success("Catálogo de nacionalidades obtenido correctamente", lista));
    }
}


