package com.proyecto.servicios.controller.onboarding;

import com.proyecto.servicios.dto.onboarding.CuentaClienteResponse;
import com.proyecto.servicios.dto.onboarding.CuentasMiasRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.onboarding.CuentaClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cuentas")
@RequiredArgsConstructor
@Tag(name = "Cuentas del cliente", description = "Cuentas con las que el cliente autenticado puede pagar")
@SecurityRequirement(name = "bearerAuth")
public class CuentaClienteController {

    private final CuentaClienteService cuentaClienteService;

    @PostMapping(value = "/mias", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Listar las cuentas activas del cliente autenticado",
            description = """
                    Requiere `Authorization: Bearer <token>` de un usuario **CLIENTE**. El cuerpo es un objeto vacío `{}`. \
                    Devuelve solo las cuentas **ACTIVAS** del titular (puede ser una lista vacía) con su saldo.

                    Errores de negocio: 403 con `data.motivo` = `NO_TITULAR` (la sesión es de un ejecutivo) o `CLIENTE_INACTIVO`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cuentas activas del cliente (lista posiblemente vacía)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "El cuerpo no es un objeto vacío", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta la sesión o expiró", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es titular (NO_TITULAR) o el cliente está inactivo (CLIENTE_INACTIVO)", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<List<CuentaClienteResponse>>> misCuentas(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CuentasMiasRequest request) {

        return ResponseEntity.ok(GenericResponse.success("Cuentas obtenidas correctamente",
                cuentaClienteService.listarMias(authorization)));
    }
}
