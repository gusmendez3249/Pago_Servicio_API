package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Una página del historial de pagos, de la más reciente a la más antigua. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialPagosResponse {

    @Schema(description = "Operaciones de la página (más recientes primero). `pinServicio` y `saldoRestante` siempre son nulos en la lista; "
            + "el detalle completo se consulta con POST /api/v1/pagos/estado")
    private List<PagoServicioResponse> elementos;

    @Schema(description = "Número de página devuelta (desde 0)", example = "0")
    private int pagina;

    @Schema(description = "Tamaño de página aplicado", example = "20")
    private int tamanio;

    @Schema(description = "Total de operaciones que cumplen los filtros", example = "57")
    private long total;

    @Schema(description = "true si hay más páginas después de esta", example = "true")
    private boolean hayMas;
}
