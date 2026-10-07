package com.proyecto.servicios.dto.onboarding;

import com.proyecto.servicios.validation.DosDecimales;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformacionLaboralDto {

    @Schema(description = "Ocupación o puesto de trabajo", example = "Desarrollador Software")
    @NotBlank(message = "La ocupación es obligatoria")
    @Size(max = 100, message = "La ocupación no puede superar los 100 caracteres")
    private String ocupacion;

    @Schema(description = "Nombre de la empresa empleadora", example = "Tech Corp")
    @NotBlank(message = "La empresa es obligatoria")
    @Size(max = 100, message = "La empresa no puede superar los 100 caracteres")
    private String empresa;

    @Schema(description = "Ingreso mensual estrictamente con dos decimales", example = "15000.00", type = "number", format = "double")
    @NotNull(message = "El ingreso mensual es obligatorio")
    @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser mayor a cero")
    @Digits(integer = 12, fraction = 2, message = "El ingreso mensual no puede tener más de 2 posiciones decimales")
    @DosDecimales(message = "El ingreso mensual debe incluir obligatoriamente exactamente 2 decimales (ejemplo: 100.00, no 100 ni 100.0000)")
    private BigDecimal ingresoMensual;
}

