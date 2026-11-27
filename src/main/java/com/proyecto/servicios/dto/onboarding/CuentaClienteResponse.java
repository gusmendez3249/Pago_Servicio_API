package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Cuenta activa del cliente autenticado, con la que puede pagar. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuentaClienteResponse {

    @Schema(description = "Número de cuenta (12 dígitos): es el que se envía en el pago", example = "695429041188")
    private String numCuenta;

    @Schema(description = "Número de cuenta enmascarado para mostrar", example = "********1188")
    private String numCuentaEnmascarada;

    @Schema(description = "Saldo disponible", example = "1000.00")
    private BigDecimal saldo;

    @Schema(description = "Estatus de la cuenta (siempre ACTIVA en este listado)", example = "ACTIVA")
    private String estatus;
}
