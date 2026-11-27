package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Página de recargas recientes (20 por página). {@code {}} es válido. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class HistorialRecargasRequest extends SolicitudEstricta {

    @Schema(description = "Número de página, desde 0", example = "0")
    @Min(value = 0, message = "La página debe ser 0 o mayor")
    @Max(value = 10_000, message = "La página debe ser de 0 a 10000")
    private Integer pagina;
}
