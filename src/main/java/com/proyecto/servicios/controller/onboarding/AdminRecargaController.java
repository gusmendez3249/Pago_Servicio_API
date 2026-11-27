package com.proyecto.servicios.controller.onboarding;

import com.proyecto.servicios.dto.onboarding.ConsultaCuentaAdminRequest;
import com.proyecto.servicios.dto.onboarding.CuentaAdminResponse;
import com.proyecto.servicios.dto.onboarding.HistorialRecargasRequest;
import com.proyecto.servicios.dto.onboarding.HistorialRecargasResponse;
import com.proyecto.servicios.dto.onboarding.RecargaSaldoRequest;
import com.proyecto.servicios.dto.onboarding.RecargaSaldoResponse;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.onboarding.RecargaSaldoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel de administrador: recargas de saldo. Todas las operaciones exigen sesión de un usuario EJECUTIVO. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Administrador", description = "Recargas de saldo a las cuentas (solo ejecutivos)")
@SecurityRequirement(name = "bearerAuth")
public class AdminRecargaController {

    private final RecargaSaldoService recargaSaldoService;

    @PostMapping(value = "/cuentas/consultar", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar una cuenta para recargarla", description = "Cuerpo: `{ \"numeroCuenta\": \"<12 dígitos>\" }`. "
            + "Devuelve el titular, el saldo y si la cuenta admite recargas. 404 si no existe; 403 si la sesión no es de un ejecutivo.")
    public ResponseEntity<GenericResponse<CuentaAdminResponse>> consultarCuenta(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ConsultaCuentaAdminRequest request) {
        return ResponseEntity.ok(GenericResponse.success("Cuenta encontrada",
                recargaSaldoService.consultarCuenta(authorization, request)));
    }

    @PostMapping(value = "/recargas", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recargar saldo a una cuenta", description = "Requiere el encabezado `Idempotency-Key` (UUID nuevo por recarga). "
            + "Cuerpo: `{ \"numeroCuenta\": \"<12 dígitos>\", \"monto\": 500.00 }` (1.00 a 50,000.00). Repetir la misma solicitud "
            + "con la misma llave devuelve la recarga original sin abonar dos veces.")
    public ResponseEntity<GenericResponse<RecargaSaldoResponse>> recargar(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(hidden = true) @RequestHeader(value = PagoServicioController.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecargaSaldoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GenericResponse.success("Recarga aplicada",
                recargaSaldoService.recargar(authorization, idempotencyKey, request)));
    }

    @PostMapping(value = "/recargas/historial", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recargas recientes", description = "20 por página, de la más reciente a la más antigua. Cuerpo: `{ \"pagina\": 0 }` o `{}`.")
    public ResponseEntity<GenericResponse<HistorialRecargasResponse>> historial(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody HistorialRecargasRequest request) {
        return ResponseEntity.ok(GenericResponse.success("Recargas obtenidas",
                recargaSaldoService.historial(authorization, request)));
    }
}
