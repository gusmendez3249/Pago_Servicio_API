package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Recarga de saldo a una cuenta, hecha por un ejecutivo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class RecargaSaldoRequest extends SolicitudEstricta {

    @Schema(description = "Número de cuenta completo (12 dígitos)", example = "580177882347")
    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^[0-9]{12}$", message = "El número de cuenta debe tener exactamente 12 dígitos")
    private String numeroCuenta;

    @Schema(description = "Monto a abonar, de 1.00 a 50,000.00", example = "500.00")
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "1.00", message = "El monto mínimo es 1.00")
    @DecimalMax(value = "50000.00", message = "El monto máximo por recarga es 50,000.00")
    @Digits(integer = 5, fraction = 2, message = "El monto admite hasta 5 enteros y 2 decimales")
    private BigDecimal monto;
}
