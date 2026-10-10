package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginCredencialesDto {

    @Schema(description = "Nombre de usuario único", example = "gustavo123")
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$", message = "El nombre de usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo")
    private String username;

    @Schema(description = "Contraseña de acceso", example = "Password123!")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[^\\p{Cntrl}]+$", message = "La contraseña debe incluir al menos una letra y un número")
    private String password;

    @Schema(description = "Identificador biométrico Face ID (opcional)", example = "987654321")
    @Positive(message = "El identificador biométrico debe ser un número positivo")
    private Long faceIdBiometrico;
}

