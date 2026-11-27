package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Elemento del catálogo de nacionalidades que se expone al cliente (la entidad JPA nunca sale del servicio). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatNacionalidadResponse {

    @Schema(description = "Identificador del registro del catálogo", example = "1")
    private Long id;

    @Schema(description = "Clave corta de la nacionalidad", example = "MEX")
    private String clave;

    @Schema(description = "Nombre exacto que se envía en datosPersonales.nacionalidad", example = "MEXICANA")
    private String nombre;

    @Schema(description = "Indica si la nacionalidad está activa", example = "true")
    private Boolean activo;
}
