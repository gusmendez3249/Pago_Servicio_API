package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class DatosContactoDto {

    @Schema(description = "Correo electrónico de contacto", example = "gustavo@example.com")
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico debe tener un formato válido")
    @Size(max = 100, message = "El correo electrónico no puede superar los 100 caracteres")
    // @Email por sí solo acepta "a@b" (sin dominio de nivel superior); se exige usuario@dominio.tld
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$",
            message = "El correo electrónico debe tener un formato válido (usuario@dominio.com)")
    private String correo;

    @Schema(description = "Número entero de teléfono móvil (10 dígitos)", example = "4181234567")
    @NotNull(message = "El teléfono móvil es obligatorio")
    @Min(value = 1000000000L, message = "El teléfono móvil debe ser un número entero de 10 dígitos (ejemplo: 4181234567)")
    @Max(value = 9999999999L, message = "El teléfono móvil debe ser un número entero de 10 dígitos (ejemplo: 4181234567)")
    private Long telefonoMovil;

    @Schema(description = "Número entero de teléfono alternativo opcional (10 dígitos)", example = "4187654321")
    @Min(value = 1000000000L, message = "El teléfono alternativo debe ser un número entero de 10 dígitos (ejemplo: 4181234567)")
    @Max(value = 9999999999L, message = "El teléfono alternativo debe ser un número entero de 10 dígitos (ejemplo: 4181234567)")
    private Long telefonoAlt;
}

