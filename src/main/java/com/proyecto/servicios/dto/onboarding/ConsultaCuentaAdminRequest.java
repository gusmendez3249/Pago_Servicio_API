package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Búsqueda de una cuenta por su número completo (panel de administrador). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ConsultaCuentaAdminRequest extends SolicitudEstricta {

    @Schema(description = "Número de cuenta completo (12 dígitos)", example = "580177882347")
    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^[0-9]{12}$", message = "El número de cuenta debe tener exactamente 12 dígitos")
    private String numeroCuenta;
}
