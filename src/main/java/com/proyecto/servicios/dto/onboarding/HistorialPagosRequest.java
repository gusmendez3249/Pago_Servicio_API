package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Consulta paginada del historial de pagos del usuario autenticado. Todo es opcional: {@code {}} es válido. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class HistorialPagosRequest extends SolicitudEstricta {

    public static final int TAMANIO_POR_DEFECTO = 20;
    public static final int TAMANIO_MAXIMO = 50;

    @Schema(description = "Filtros opcionales (estatus y rango de fechas)")
    @Valid
    private FiltrosHistorialDto filtros;

    @Schema(description = "Número de página, desde 0 (por defecto 0)", example = "0", minimum = "0")
    @Min(value = 0, message = "La página debe ser 0 o mayor")
    @Max(value = MAX_PAGINA, message = "La página debe ser de 0 a " + MAX_PAGINA)
    private Integer pagina;

    /** Tope de página: evita que pagina*tamaño desborde el offset de la consulta (antes respondía 500). */
    public static final int MAX_PAGINA = 10_000;

    @Schema(description = "Elementos por página, de 1 a 50 (por defecto 20)", example = "20", minimum = "1", maximum = "50")
    @Min(value = 1, message = "El tamaño de página debe ser de 1 a " + TAMANIO_MAXIMO)
    @Max(value = TAMANIO_MAXIMO, message = "El tamaño de página debe ser de 1 a " + TAMANIO_MAXIMO)
    private Integer tamanio;

    public int paginaEfectiva() {
        return pagina == null ? 0 : pagina;
    }

    public int tamanioEfectivo() {
        return tamanio == null ? TAMANIO_POR_DEFECTO : tamanio;
    }

    public FiltrosHistorialDto filtrosEfectivos() {
        return filtros == null ? new FiltrosHistorialDto() : filtros;
    }
}
