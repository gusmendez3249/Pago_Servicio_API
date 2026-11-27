package com.proyecto.servicios.controller.onboarding;

import com.proyecto.servicios.dto.onboarding.ConciliacionPagoRequest;
import com.proyecto.servicios.dto.onboarding.EstadoPagoRequest;
import com.proyecto.servicios.dto.onboarding.HistorialPagosRequest;
import com.proyecto.servicios.dto.onboarding.HistorialPagosResponse;
import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.onboarding.ConciliacionPagoService;
import com.proyecto.servicios.service.onboarding.EstadoPagoService;
import com.proyecto.servicios.service.onboarding.HistorialPagoService;
import com.proyecto.servicios.service.onboarding.PagoServicioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@Tag(name = "Pago de servicios", description = "Transacción de pago de servicios (sendTx de GestoPago) con sesión, PIN 2FA e idempotencia")
@SecurityRequirement(name = "bearerAuth")
public class PagoServicioController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PagoServicioService pagoServicioService;
    private final ConciliacionPagoService conciliacionPagoService;
    private final EstadoPagoService estadoPagoService;
    private final HistorialPagoService historialPagoService;

    @PostMapping(value = "/servicio", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Pagar un servicio con el saldo de la cuenta",
            description = """
                    Requiere `Authorization: Bearer <token>` de un usuario **CLIENTE** titular de la cuenta y el encabezado \
                    `Idempotency-Key` (UUID nuevo por cada pago). Reenviar la misma solicitud con la misma llave **no cobra \
                    de nuevo**: devuelve el resultado registrado.

                    Flujo: sesión → cuenta propia, cliente y cuenta activos → PIN `twoFA` (3 intentos, bloqueo 15 min) → \
                    servicio/producto/monto contra el catálogo → **reserva del saldo** → sendTx cifrado (AES-256-CBC, TLS 1.2+) → \
                    CODIGO `01` confirma (la respuesta trae el `folio`); un rechazo reintegra el saldo y responde 422 con el `PagoServicioResponse` \
                    (estatus `RECHAZADA` y `motivo`) en `data`.

                    Si no se puede saber si GestoPago aplicó el pago (timeout, 5xx, respuesta ilegible) se responde **202** \
                    con estatus `INDETERMINADA` (o `PENDIENTE`): el monto queda retenido hasta la conciliación (confirmTx) y **no** debe repetirse el pago; \
                    consulte el resultado con `POST /api/v1/pagos/estado`.

                    **Errores de negocio:** `data` trae un objeto con `motivo` (SALDO_INSUFICIENTE_CUENTA, MONTO_NO_PERMITIDO, \
                    PRODUCTO_NO_PAGABLE, IDEMPOTENCY_KEY_INVALIDA, PIN_INCORRECTO con `intentosRestantes`, SIN_PIN, CUENTA_INACTIVA, NO_TITULAR, \
                    CUENTA_NO_ENCONTRADA, PRODUCTO_NO_ENCONTRADO, LLAVE_EN_USO, LLAVE_OTROS_DATOS, PIN_BLOQUEADO con `minutosBloqueo`, \
                    VALIDACION_REQUERIDA, PROVEEDOR_NO_DISPONIBLE). El texto del proveedor nunca se devuelve.

                    **Teléfono o referencia (nunca ambos):** con `esReferencia = false` envíe solo `telefono` (10 dígitos); \
                    con `esReferencia = true` envíe solo `reference` (1 a 100 alfanuméricos). Campos extra se rechazan.
                    """,
            parameters = @Parameter(name = IDEMPOTENCY_KEY, in = ParameterIn.HEADER, required = true,
                    description = "UUID único por pago", example = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pago CONFIRMADO (CODIGO 01) con folio", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "202", description = "Pago INDETERMINADO o PENDIENTE: en verificación, monto retenido (sin folio todavía)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, Idempotency-Key ausente o inválida, monto fuera de catálogo o saldo insuficiente", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta la sesión o expiró", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "No es cliente titular, cliente/cuenta inactivo o PIN incorrecto", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "La cuenta no pertenece al cliente o el servicio/producto no existe", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key reutilizada con otros datos o en uso simultáneo", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "422", description = "GestoPago rechazó el pago; el saldo se reintegró. `data` es el PagoServicioResponse (RECHAZADA) con `motivo`: ERROR_GESTOPAGO (Gesto Pago respondió 05 o 55), REFERENCIA_INVALIDA, SERVICIO_NO_DISPONIBLE o RECHAZADO_PROVEEDOR", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "423", description = "PIN bloqueado por intentos fallidos", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno de configuración; el saldo no se tocó (motivo PROVEEDOR_NO_DISPONIBLE)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "502", description = "El proveedor no procesó la petición (conexión/credenciales); el saldo se reintegró (motivo PROVEEDOR_NO_DISPONIBLE)", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<PagoServicioResponse>> pagarServicio(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(hidden = true) @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PagoServicioRequest request) {

        PagoServicioResponse response = pagoServicioService.procesarPago(authorization, idempotencyKey, request);
        if (response.getEstatus() == EstatusTransaccionPago.CONFIRMADA) {
            return ResponseEntity.ok(GenericResponse.success("Pago realizado correctamente", response));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(GenericResponse.success("Pago en verificación con el proveedor", response));
    }

    @PostMapping(value = "/estado", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Consultar el estado de un pago propio",
            description = """
                    Requiere `Authorization: Bearer <token>` de un usuario **CLIENTE**. Cuerpo: exactamente uno de `{ "upc": "K3J9X0A7ZQ" }` \
                    o `{ "idempotencyKey": "<uuid>" }`. Devuelve el `PagoServicioResponse` con cualquier estatus (CONFIRMADA con folio, \
                    RECHAZADA con motivo, INDETERMINADA o PENDIENTE). Solo se consultan operaciones del usuario autenticado: una \
                    operación inexistente o ajena responde igual (404 `OPERACION_NO_ENCONTRADA`).

                    Si la operación está INDETERMINADA y ya pasaron 62 s desde que se registró (y menos de 24 h), el servidor consulta \
                    confirmTx antes de responder: CODIGO 06 la confirma y 70 la rechaza reintegrando el monto una sola vez.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estado de la operación (cualquier estatus)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos: se debe enviar exactamente uno de upc o idempotencyKey", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta la sesión o expiró", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es titular (NO_TITULAR)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "La operación no existe o es de otro usuario (OPERACION_NO_ENCONTRADA)", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<PagoServicioResponse>> estado(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody EstadoPagoRequest request) {

        return ResponseEntity.ok(GenericResponse.success("Estado de la operación", estadoPagoService.consultar(authorization, request)));
    }

    @PostMapping(value = "/historial", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Historial de pagos propios (paginado)",
            description = """
                    Requiere `Authorization: Bearer <token>` de un usuario **CLIENTE**. Devuelve exclusivamente las operaciones del \
                    usuario autenticado, de la más reciente a la más antigua. Cuerpo opcional (`{}` es válido): \
                    `{ "filtros": { "estatus": "CONFIRMADA", "fechaDesde": "2026-11-01", "fechaHasta": "2026-11-14" }, "pagina": 0, "tamanio": 20 }`. \
                    `fechaDesde` y `fechaHasta` son `yyyy-MM-dd` estrictas e inclusivas (días completos); `tamanio` de 1 a 50 (por defecto 20).

                    Cada elemento tiene los campos de `PagoServicioResponse` más `idServicio`, `idProducto` y `logoUrl`; en la lista \
                    `pinServicio` y `saldoRestante` siempre son nulos. El detalle completo se consulta con `POST /api/v1/pagos/estado`.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página del historial (`elementos`, `pagina`, `tamanio`, `total`, `hayMas`)", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos: estatus desconocido, fechas mal formadas o invertidas, página o tamaño fuera de rango, campos extra", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta la sesión o expiró", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es titular (NO_TITULAR)", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<HistorialPagosResponse>> historial(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody HistorialPagosRequest request) {

        return ResponseEntity.ok(GenericResponse.success("Historial de pagos", historialPagoService.listar(authorization, request)));
    }

    @PostMapping(value = "/conciliacion", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Conciliar manualmente un pago INDETERMINADO (solo EJECUTIVO)",
            description = "Con base en el reporte de GestoPago: `aplicada = true` confirma el pago; `aplicada = false` "
                    + "lo marca como no aplicado y reintegra el monto a la cuenta."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operación conciliada", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta la sesión o expiró", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es ejecutivo", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe la operación", content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "409", description = "La operación no está INDETERMINADA", content = @Content(schema = @Schema(implementation = GenericResponse.class)))
    })
    public ResponseEntity<GenericResponse<PagoServicioResponse>> conciliar(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ConciliacionPagoRequest request) {

        PagoServicioResponse response = conciliacionPagoService.resolverManual(authorization, request);
        return ResponseEntity.ok(GenericResponse.success("Operación conciliada", response));
    }
}
