package com.proyecto.servicios.dto.onboarding;

import com.proyecto.servicios.enums.EstatusTransaccionPago;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoServicioResponse {

    @Schema(description = "Número de cuenta enmascarado (solo los últimos 4 dígitos)", example = "********1188")
    private String numCuenta;

    @Schema(description = "Identificador único de la operación (upc) ante GestoPago: 10 caracteres", example = "K3J9X0A7ZQ")
    private String upc;

    @Schema(description = "CONFIRMADA (pago aplicado) o INDETERMINADA (en verificación con el proveedor, monto retenido)")
    private EstatusTransaccionPago estatus;

    @Schema(description = "Monto del pago", example = "100.50")
    private BigDecimal monto;

    @Schema(description = "Saldo disponible después del pago (el monto de un pago INDETERMINADO sigue retenido)", example = "899.50")
    private BigDecimal saldoRestante;

    @Schema(description = "Código de respuesta de GestoPago (\"01\" en un pago confirmado)", example = "01")
    private String codigoRespuesta;

    @Schema(description = "Mensaje sobre el resultado de la operación")
    private String mensajeRespuesta;

    @Schema(description = "Fecha y hora en que se registró la operación")
    private LocalDateTime fechaTransaccion;
}
