package com.proyecto.servicios.dto.onboarding;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.proyecto.servicios.validation.MayorDeEdad;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatosPersonalesDto {

    @Schema(description = "Primer nombre del cliente", example = "Gustavo")
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$", message = "El nombre solo debe contener letras y espacios")
    private String nombre;

    @Schema(description = "Segundo nombre opcional", example = "Adolfo")
    @Size(max = 50, message = "El segundo nombre debe tener máximo 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]*$", message = "El segundo nombre solo debe contener letras y espacios")
    private String segundoNombre;

    @Schema(description = "Apellido paterno del cliente", example = "Mendez")
    @NotBlank(message = "El apellido paterno es obligatorio")
    @Size(min = 3, max = 50, message = "El apellido paterno debe tener mínimo 3 y máximo 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$", message = "El apellido paterno solo debe contener letras y espacios")
    private String apellidoPaterno;

    @Schema(description = "Apellido materno del cliente", example = "Rodriguez")
    @NotBlank(message = "El apellido materno es obligatorio")
    @Size(min = 3, max = 50, message = "El apellido materno debe tener mínimo 3 y máximo 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$", message = "El apellido materno solo debe contener letras y espacios")
    private String apellidoMaterno;

    @Schema(description = "Fecha de nacimiento en formato estrictamente año-mes-día (yyyy-MM-dd)", example = "1995-05-15", type = "string", format = "date")
    @NotNull(message = "La fecha de nacimiento es obligatoria (formato yyyy-MM-dd)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @MayorDeEdad
    private LocalDate fechaNacimiento;

    @Schema(description = "CURP a 18 caracteres", example = "MERG950515HMNDRR01")
    @NotBlank(message = "La CURP es obligatoria")
    @Size(min = 18, max = 18, message = "La CURP debe tener exactamente 18 caracteres")
    @Pattern(
            regexp = "^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$",
            message = "Formato de CURP no válido"
    )
    private String curp;

    @Schema(description = "RFC a 12 o 13 caracteres", example = "MERG950515AB1")
    @NotBlank(message = "El RFC es obligatorio")
    @Size(min = 12, max = 13, message = "El RFC debe tener 12 o 13 caracteres")
    @Pattern(
            regexp = "^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$",
            message = "Formato de RFC no válido"
    )
    private String rfc;

    @Schema(description = "Sexo (MASCULINO, FEMENINO, OTRO)", example = "MASCULINO")
    @NotBlank(message = "El sexo es obligatorio")
    @Pattern(
            regexp = "^(?i)(MASCULINO|FEMENINO|OTRO)$",
            message = "El sexo solo permite opciones válidas del catálogo (MASCULINO, FEMENINO, OTRO)"
    )
    private String sexo;

    @Schema(description = "Nacionalidad del catálogo", example = "MEXICANA")
    @NotBlank(message = "La nacionalidad es obligatoria")
    @Size(max = 50, message = "La nacionalidad no puede superar los 50 caracteres")
    private String nacionalidad;

    @Schema(description = "Estado civil (SOLTERO, CASADO, DIVORCIADO, VIUDO, UNION LIBRE)", example = "SOLTERO")
    @NotBlank(message = "El estado civil es obligatorio")
    @Pattern(
            regexp = "^(?i)(SOLTERO|SOLTERA|CASADO|CASADA|DIVORCIADO|DIVORCIADA|VIUDO|VIUDA|UNION LIBRE|UNION_LIBRE)$",
            message = "El estado civil solo permite opciones válidas del catálogo (SOLTERO/A, CASADO/A, DIVORCIADO/A, VIUDO/A, UNION LIBRE)"
    )
    private String estadoCivil;

    @Schema(description = "Identificador biométrico Face ID (opcional)", example = "987654321")
    private Long datosBiometricos;
}

