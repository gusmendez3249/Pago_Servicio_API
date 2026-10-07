package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
    @Size(max = 50, message = "El nombre de usuario no puede superar los 50 caracteres")
    private String username;

    @Schema(description = "Contraseña de acceso", example = "Password123!")
    @Size(max = 100, message = "La contraseña no puede superar los 100 caracteres")
    private String password;

    @Schema(description = "Identificador biométrico Face ID (opcional)", example = "987654321")
    private Long faceIdBiometrico;
}

