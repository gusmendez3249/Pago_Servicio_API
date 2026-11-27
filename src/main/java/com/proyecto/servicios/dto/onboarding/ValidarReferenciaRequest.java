package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Solicitud de validación de referencia. Estricta: campos extra se rechazan (SolicitudEstricta). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ValidarReferenciaRequest extends SolicitudEstricta {

    @Schema(description = "Identificador del servicio en GestoPago (entero positivo)", example = "1")
    @NotNull(message = "El identificador del servicio (idSer) es obligatorio")
    @Positive(message = "El identificador del servicio (idSer) debe ser un entero positivo")
    @Max(value = PagoServicioRequest.MAX_ID_CATALOGO, message = "El identificador del servicio (idSer) no es válido")
    private Integer idSer;

    @Schema(description = "Identificador del producto en GestoPago (entero positivo)", example = "2")
    @NotNull(message = "El identificador del producto (idProd) es obligatorio")
    @Positive(message = "El identificador del producto (idProd) debe ser un entero positivo")
    @Max(value = PagoServicioRequest.MAX_ID_CATALOGO, message = "El identificador del producto (idProd) no es válido")
    private Integer idProd;

    @Schema(description = "Referencia: 1 a 100 caracteres alfanuméricos sin espacios", example = "0012345678")
    @NotNull(message = "La referencia es obligatoria")
    @Pattern(regexp = PagoServicioRequest.PATRON_REFERENCIA,
            message = "La referencia debe tener de 1 a 100 caracteres alfanuméricos, sin espacios ni símbolos")
    private String referencia;
}
