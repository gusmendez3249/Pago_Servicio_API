package com.proyecto.servicios.dto.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotNull(message = "La bandera de autenticación es obligatoria (1 = Contraseña, 2 = Biométrico Face ID)")
    private Integer bandera;

    @NotBlank(message = "El nombre de usuario es SIEMPRE obligatorio")
    private String username;

    private String password;

    private Long faceIdBiometrico;
}
