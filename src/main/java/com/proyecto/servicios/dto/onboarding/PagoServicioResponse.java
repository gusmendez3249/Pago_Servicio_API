package com.proyecto.servicios.dto.onboarding;

import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.enums.OrigenConfirmacion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resultado de una operación de pago. Se devuelve en 200 (CONFIRMADA), 202 (INDETERMINADA / PENDIENTE), 422
 * (RECHAZADA, dentro de {@code data}) y en {@code POST /api/v1/pagos/estado}. Ningún texto del proveedor llega aquí.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoServicioResponse {

    @Schema(description = "Número de cuenta enmascarado (solo los últimos 4 dígitos)", example = "********1188")
    private String numCuenta;

    @Schema(description = "Identificador único de la operación (upc) ante GestoPago: 10 caracteres", example = "K3J9X0A7ZQ")
    private String upc;

    @Schema(description = "CONFIRMADA (pago aplicado), RECHAZADA (no se aplicó, saldo reintegrado), INDETERMINADA "
            + "(en verificación con el proveedor, monto retenido) o PENDIENTE (envío en curso)")
    private EstatusTransaccionPago estatus;

    @Schema(description = "Motivo del rechazo (solo con estatus RECHAZADA): REFERENCIA_INVALIDA, SERVICIO_NO_DISPONIBLE, "
            + "RECHAZADO_PROVEEDOR, ERROR_GESTOPAGO (error propio de Gesto Pago: 05 o 55) o PROVEEDOR_NO_DISPONIBLE; nulo en los demás estatus", example = "REFERENCIA_INVALIDA")
    private MotivoPago motivo;

    @Schema(description = "Solo con estatus CONFIRMADA: GESTOPAGO si el proveedor aplicó el pago, SIMULADO si GestoPago no "
            + "funcionó y el pago se dio por exitoso en un modo de prueba ya retirado (solo registros históricos); nulo en los demás estatus", example = "GESTOPAGO")
    private OrigenConfirmacion origenConfirmacion;

    @Schema(description = "true si es un pago histórico SIMULADO (no lo aplicó GestoPago)", example = "false")
    private boolean simulado;

    @Schema(description = "Monto del pago", example = "100.50")
    private BigDecimal monto;

    @Schema(description = "Saldo disponible después del pago (el monto de un pago INDETERMINADO sigue retenido)", example = "899.50")
    private BigDecimal saldoRestante;

    @Schema(description = "Folio de la operación (NUM_AUTORIZACION del proveedor); nulo si aún no hay confirmación", example = "0098765")
    private String folio;

    @Schema(description = "Identificador de la transacción del proveedor (ID_TX); nulo si aún no hay confirmación")
    private String idTx;

    @Schema(description = "Lo que se muestra al usuario como número de operación: el folio; si no hay, idTx; si no hay, el upc",
            example = "0098765")
    private String identificadorOperacion;

    @Schema(description = "Comisión informativa del proveedor (no se cobra al usuario); nula si no se conoce", example = "0.00")
    private BigDecimal comision;

    @Schema(description = "PIN/código de la tarjeta de regalo. Solo con estatus CONFIRMADA y si el producto lo entrega; "
            + "es secreto: se muestra únicamente en el comprobante")
    @ToString.Exclude
    private String pinServicio;

    @Schema(description = "Leyenda informativa del proveedor (texto plano, solo con estatus CONFIRMADA); nula si no hay")
    private String leyenda;

    @Schema(description = "Nombre del servicio pagado", example = "Telcel")
    private String servicio;

    @Schema(description = "Nombre del producto pagado", example = "Recarga $100")
    private String producto;

    @Schema(description = "Referencia enmascarada (solo los últimos 4 caracteres); solo en pagos con referencia", example = "******5678")
    private String referenciaEnmascarada;

    @Schema(description = "Teléfono enmascarado (solo los últimos 4 dígitos); solo en pagos con teléfono", example = "******5678")
    private String telefonoEnmascarado;

    @Schema(description = "Código de respuesta de GestoPago (\"01\" en un pago confirmado)", example = "01")
    private String codigoRespuesta;

    @Schema(description = "Mensaje fijo del sistema sobre el resultado (nunca el texto del proveedor)")
    private String mensajeRespuesta;

    @Schema(description = "Identificador del servicio en el catálogo", example = "1")
    private Integer idServicio;

    @Schema(description = "Identificador del producto en el catálogo", example = "2")
    private Integer idProducto;

    @Schema(description = "URL del logo del proveedor; nula si no hay idServicio", example = "https://host/sistema/images/gestopago/servicios/1.png")
    private String logoUrl;

    @Schema(description = "Fecha y hora en que se registró la operación")
    private LocalDateTime fechaTransaccion;
}
