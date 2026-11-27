package com.proyecto.servicios.dto.onboarding;

import com.proyecto.servicios.validation.EstadoPagoValido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Consulta del estado de un pago propio: exactamente uno de {@code upc} o {@code idempotencyKey}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@EstadoPagoValido
public class EstadoPagoRequest extends SolicitudEstricta {

    public static final String PATRON_UPC = "^[A-Z0-9]{10}$";
    public static final String PATRON_UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    @Schema(description = "Identificador de la operación: exactamente 10 caracteres [A-Z0-9]", example = "K3J9X0A7ZQ")
    @Pattern(regexp = PATRON_UPC, message = "El upc debe tener exactamente 10 caracteres en mayúsculas y dígitos")
    private String upc;

    @Schema(description = "Idempotency-Key (UUID) con la que se envió el pago", example = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c")
    @Pattern(regexp = PATRON_UUID, message = "La idempotencyKey debe ser un UUID")
    private String idempotencyKey;
}
