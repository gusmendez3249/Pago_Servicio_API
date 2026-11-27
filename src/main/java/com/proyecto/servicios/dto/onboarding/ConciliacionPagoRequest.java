package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Resolución manual de un pago INDETERMINADO con base en el reporte del proveedor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ConciliacionPagoRequest extends SolicitudEstricta {

    @Schema(description = "Identificador de la operación: exactamente 10 caracteres [A-Z0-9]", example = "K3J9X0A7ZQ")
    @NotNull(message = "El upc es obligatorio")
    @Pattern(regexp = "^[A-Z0-9]{10}$", message = "El upc debe tener exactamente 10 caracteres en mayúsculas y dígitos")
    private String upc;

    @Schema(description = "true = GestoPago confirmó que el pago se aplicó; false = confirmó que NO se aplicó", example = "true")
    @NotNull(message = "La bandera aplicada es obligatoria")
    private Boolean aplicada;
}
