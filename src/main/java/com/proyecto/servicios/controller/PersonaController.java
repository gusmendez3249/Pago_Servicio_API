package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonasRequest;
import com.proyecto.servicios.service.PersonaService;
import com.proyecto.servicios.service.onboarding.SesionService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Personas", description = "Endpoints para la gestión de personas (Creación, Actualización y Eliminación). Solo EJECUTIVO.")
@SecurityRequirement(name = "bearerAuth")
public class PersonaController {

    @Autowired
    private PersonaService personaService;

    @Autowired
    private SesionService sesionService;

    /** Alta, cambio y borrado físico de personas: solo un ejecutivo autenticado (401 sin sesión, 403 si es cliente). */
    private void exigirEjecutivo(String authorization) {
        if (!sesionService.autenticar(authorization).esEjecutivo()) {
            throw new AccesoDenegadoException("Solo un ejecutivo puede administrar personas.");
        }
    }

    @PostMapping(value = "/personas", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear persona", description = "Registra una nueva persona en el sistema.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Persona creada exitosamente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de solicitud inválidos o validación fallida", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta la sesión o expiró", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es ejecutivo", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse> crearUser(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody PersonasRequest personasRequest) {
        exigirEjecutivo(authorization);
        return new ResponseEntity<>(personaService.creaPersona(personasRequest), HttpStatus.CREATED);
    }

    @PutMapping(value = "/personasActualiza", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar persona", description = "Actualiza los apellidos de una persona por nombre.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Persona actualizada exitosamente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Petición inválida o datos incorrectos", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "La persona no fue encontrada", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta la sesión o expiró", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es ejecutivo", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse> actualizUser(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody PersonasRequest personasRequest) {
        exigirEjecutivo(authorization);
        // Si la persona no existe el servicio lanza RecursoNoEncontradoException (404 en GlobalExceptionHandler)
        return ResponseEntity.ok(personaService.actualizaPersona(personasRequest));
    }

    @PutMapping(value = "/personasElimina", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Eliminar persona", description = "Elimina una persona del sistema por su nombre.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Persona eliminada exitosamente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Petición inválida o datos incorrectos", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "La persona no fue encontrada", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta la sesión o expiró", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es ejecutivo", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse> eliminaUser(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody EliminaPersonaRequest personasRequest) {
        exigirEjecutivo(authorization);
        // Si la persona no existe el servicio lanza RecursoNoEncontradoException (404 en GlobalExceptionHandler)
        return ResponseEntity.ok(personaService.eliminaPersona(personasRequest));
    }
}


