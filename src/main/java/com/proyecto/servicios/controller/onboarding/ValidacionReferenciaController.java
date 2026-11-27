package com.proyecto.servicios.controller.onboarding;

import com.proyecto.servicios.dto.onboarding.ValidarReferenciaRequest;
import com.proyecto.servicios.dto.onboarding.ValidarReferenciaResponse;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.onboarding.ValidacionReferenciaService;
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

@RestController
@RequestMapping("/api/v1/pagos/referencia")
@RequiredArgsConstructor
@Tag(name = "Validación de referencia", description = "Verificación de la referencia con GestoPago (verifyReference) previa al pago")
@SecurityRequirement(name = "bearerAuth")
public class ValidacionReferenciaController {

    private final ValidacionReferenciaService validacionReferenciaService;

    @PostMapping(value = "/validar", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Validar una referencia antes de pagar",
            description = """
                    Requiere `Authorization: Bearer <token>` de un usuario **CLIENTE**. Aplica a productos de pago \
                    (`tipoFront` 2) y de consulta (`tipoFront` 4).

                    Responde 200 con `estado` **VALIDA** o **INVALIDA** (referencia inexistente). \
                    `verificacion` = **EN_LINEA** si se consultó a GestoPago (servicio con producto de consulta): incluye \
                    `situacion` (ADEUDO, SIN_ADEUDO, SALDO_A_FAVOR) y `saldo`, y solo se puede pagar con adeudo, por el \
                    total (`montoAPagar`). `verificacion` = **SOLO_FORMATO** si el proveedor no ofrece consulta: no se \
                    llama a GestoPago, `saldo` y `situacion` son null y el monto lo captura el usuario. Cuando \
                    `puedePagar` es true se devuelve un `idValidacion` de **un solo uso** con `vigenciaSegundos` de vigencia, que debe \
                    enviarse en el pago (`POST /api/v1/pagos/servicio`, campo `idValidacion`). Sin él (o vencido, usado \
                    o de otra referencia) el pago responde 428.

                    Nunca se devuelve texto del proveedor, solo enumerados.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Referencia consultada (VALIDA o INVALIDA)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o producto que no admite verificación", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta la sesión o expiró", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es cliente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "El servicio/producto no existe", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error de configuración o cifrado", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "502", description = "El proveedor respondió con error o algo ilegible", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "503", description = "Almacén temporal no disponible", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "504", description = "El proveedor no respondió a tiempo", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<ValidarReferenciaResponse>> validar(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ValidarReferenciaRequest request) {

        ValidarReferenciaResponse response = validacionReferenciaService.validar(authorization, request);
        return ResponseEntity.ok(GenericResponse.success("Consulta de referencia completada", response));
    }
}
