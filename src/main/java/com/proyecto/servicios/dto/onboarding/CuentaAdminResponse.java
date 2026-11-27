package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Cuenta encontrada en el panel: lo justo para confirmar a quién se le abona. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuentaAdminResponse {

    @Schema(description = "Número de cuenta enmascarado", example = "********2347")
    private String numCuentaEnmascarada;

    @Schema(description = "Nombre del titular", example = "Cliente Prueba Uno")
    private String titular;

    @Schema(description = "Saldo actual", example = "0.00")
    private BigDecimal saldo;

    @Schema(description = "ACTIVA o INACTIVA (solo se recargan cuentas activas)", example = "ACTIVA")
    private String estatus;

    @Schema(description = "true si la cuenta y su titular están activos", example = "true")
    private boolean recargable;
}
