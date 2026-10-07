package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @Schema(description = "Bandera de autenticación (1 = Contraseña, 2 = Biométrico Face ID)", example = "1")
    @NotNull(message = "La bandera de autenticación es obligatoria (1 = Contraseña, 2 = Biométrico Face ID)")
    @Min(value = 1, message = "La bandera de autenticación solo permite los enteros 1 (Contraseña) o 2 (Biométrico Face ID)")
    @Max(value = 2, message = "La bandera de autenticación solo permite los enteros 1 (Contraseña) o 2 (Biométrico Face ID)")
    private Integer bandera;

    @Schema(description = "Nombre de usuario", example = "gustavo123")
    @NotBlank(message = "El nombre de usuario es SIEMPRE obligatorio")
    @Size(max = 50, message = "El nombre de usuario no puede superar los 50 caracteres")
    private String username;

    @Schema(description = "Contraseña para autenticación por contraseña (bandera = 1)", example = "Password123!")
    @Size(max = 100, message = "La contraseña no puede superar los 100 caracteres")
    private String password;

    @Schema(description = "Identificador biométrico Face ID para bandera = 2", example = "987654321")
    private Long faceIdBiometrico;
}

