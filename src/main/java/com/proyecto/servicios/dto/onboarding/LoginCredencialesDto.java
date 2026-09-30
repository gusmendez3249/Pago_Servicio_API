package com.proyecto.servicios.dto.onboarding;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginCredencialesDto {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    private String password;

    private Long faceIdBiometrico;
}
