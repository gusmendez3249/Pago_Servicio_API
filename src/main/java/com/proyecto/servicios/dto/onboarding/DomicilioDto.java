package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomicilioDto {

    @Schema(description = "Nombre de la calle", example = "Hidalgo")
    @NotBlank(message = "La calle es obligatoria")
    @Size(max = 100, message = "La calle no puede superar los 100 caracteres")
    private String calle;

    @Schema(description = "Número exterior", example = "123")
    @NotBlank(message = "El número exterior es obligatorio")
    @Size(max = 20, message = "El número exterior no puede superar los 20 caracteres")
    private String numeroExterior;

    @Schema(description = "Número interior opcional", example = "A")
    @Size(max = 20, message = "El número interior no puede superar los 20 caracteres")
    private String numeroInterior;

    @Schema(description = "Colonia o asentamiento", example = "Centro")
    @NotBlank(message = "La colonia es obligatoria")
    @Size(max = 100, message = "La colonia no puede superar los 100 caracteres")
    private String colonia;

    @Schema(description = "Municipio o alcaldía", example = "Dolores Hidalgo")
    @NotBlank(message = "El municipio es obligatorio")
    @Size(max = 100, message = "El municipio no puede superar los 100 caracteres")
    private String municipio;

    @Schema(description = "Estado", example = "Guanajuato")
    @NotBlank(message = "El estado es obligatorio")
    @Size(max = 100, message = "El estado no puede superar los 100 caracteres")
    private String estado;

    @Schema(description = "Código postal de 5 dígitos", example = "37800")
    @NotBlank(message = "El código postal es obligatorio")
    @Pattern(regexp = "^\\d{5}$", message = "El código postal debe contener exactamente 5 dígitos")
    private String codigoPostal;

    @Schema(description = "País", example = "MEXICO")
    @NotBlank(message = "El país es obligatorio")
    @Size(max = 100, message = "El país no puede superar los 100 caracteres")
    private String pais;
}

