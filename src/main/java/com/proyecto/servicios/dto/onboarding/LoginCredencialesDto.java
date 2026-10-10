package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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

    // Política: 8 a 72 caracteres, sin espacios, con al menos 1 mayúscula, 1 minúscula, 1 número y
    // 1 carácter especial (signo de puntuación ASCII: ! " # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \\ ] ^ _ ` { | } ~)
    public static final String POLITICA_PASSWORD =
            "^(?=.*\\p{Lower})(?=.*\\p{Upper})(?=.*\\d)(?=.*\\p{Punct})[^\\s\\p{Cntrl}]{8,72}$";

    @Schema(description = "Contraseña (al registrar o al editar): mínimo 8 caracteres, con al menos 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial; sin espacios", example = "Password123!")
    @Pattern(regexp = POLITICA_PASSWORD,
            message = "La contraseña debe tener de 8 a 72 caracteres, sin espacios, con al menos 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial")
    private String password;

    @Schema(description = "Identificador biométrico Face ID (opcional)", example = "987654321")
    @Positive(message = "El identificador biométrico debe ser un número positivo")
    private Long faceIdBiometrico;
}

