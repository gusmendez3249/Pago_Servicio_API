package com.proyecto.servicios.dto.onboarding;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.proyecto.servicios.enums.MotivoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contenido de {@code data} en los errores de negocio de pagos y cuentas: un motivo enumerado y datos de apoyo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetalleErrorPago {

    @Schema(description = "Motivo enumerado del error", example = "PIN_INCORRECTO")
    private MotivoPago motivo;

    @Schema(description = "Intentos de PIN que quedan antes del bloqueo (solo en PIN_INCORRECTO)", example = "2")
    private Integer intentosRestantes;

    @Schema(description = "Minutos que dura el bloqueo del PIN (solo en PIN_BLOQUEADO)", example = "15")
    private Integer minutosBloqueo;

    public static DetalleErrorPago de(MotivoPago motivo) {
        return DetalleErrorPago.builder().motivo(motivo).build();
    }
}
