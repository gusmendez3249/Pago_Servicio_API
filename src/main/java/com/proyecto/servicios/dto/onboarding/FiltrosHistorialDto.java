package com.proyecto.servicios.dto.onboarding;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.validation.FechaEstrictaDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Filtros opcionales del historial de pagos. Las fechas son días completos (inclusivos) en la zona de las operaciones. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class FiltrosHistorialDto extends SolicitudEstricta {

    @Schema(description = "Solo operaciones con este estatus: CONFIRMADA, RECHAZADA, INDETERMINADA o PENDIENTE", example = "CONFIRMADA")
    private EstatusTransaccionPago estatus;

    @Schema(description = "Desde este día (inclusive), formato estricto yyyy-MM-dd", example = "2026-11-01", type = "string", format = "date")
    @JsonDeserialize(using = FechaEstrictaDeserializer.class)
    private LocalDate fechaDesde;

    @Schema(description = "Hasta este día (inclusive, el día completo), formato estricto yyyy-MM-dd", example = "2026-11-14", type = "string", format = "date")
    @JsonDeserialize(using = FechaEstrictaDeserializer.class)
    private LocalDate fechaHasta;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "La fecha 'fechaDesde' no puede ser posterior a 'fechaHasta'")
    public boolean isRangoFechasValido() {
        return fechaDesde == null || fechaHasta == null || !fechaDesde.isAfter(fechaHasta);
    }
}
