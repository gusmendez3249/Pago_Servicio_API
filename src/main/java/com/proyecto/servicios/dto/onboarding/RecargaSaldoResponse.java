package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Recarga aplicada (o una recarga del historial). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecargaSaldoResponse {

    @Schema(description = "Folio de la recarga", example = "R-000123")
    private String folio;

    @Schema(description = "Número de cuenta enmascarado", example = "********2347")
    private String numCuentaEnmascarada;

    @Schema(description = "Nombre del titular", example = "Cliente Prueba Uno")
    private String titular;

    @Schema(description = "Monto abonado", example = "500.00")
    private BigDecimal monto;

    @Schema(description = "Saldo de la cuenta después de la recarga", example = "500.00")
    private BigDecimal saldoResultante;

    @Schema(description = "Fecha y hora de la recarga")
    private LocalDateTime fecha;
}
