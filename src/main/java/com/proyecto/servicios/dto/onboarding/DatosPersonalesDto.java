package com.proyecto.servicios.dto.onboarding;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.validation.FechaEstrictaDeserializer;
import com.proyecto.servicios.validation.MayorDeEdad;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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

    // Palabras de letras separadas por UN espacio: sin tabuladores, saltos de línea ni espacios al inicio/fin
    private static final String PALABRAS = "[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ]+( [a-zA-ZáéíóúÁÉÍÓÚüÜñÑ]+)*$";
    private static final String NOMBRE_REGEX = "^" + PALABRAS;
    // Mes 01-12 y día 01-31 de la fecha embebida en CURP / RFC
    private static final String MES_DIA = "(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])";
    // Claves de entidad federativa válidas en la CURP (NE = nacido en el extranjero)
    private static final String ENTIDADES = "(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)";

    @Schema(description = "Primer nombre del cliente", example = "Gustavo")
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
    @Pattern(regexp = NOMBRE_REGEX, message = "El nombre solo debe contener letras y espacios simples entre palabras")
    private String nombre;

    @Schema(description = "Segundo nombre opcional", example = "Adolfo")
    @Size(max = 50, message = "El segundo nombre debe tener máximo 50 caracteres")
    @Pattern(regexp = "^$|^(?=.{3,50}$)" + PALABRAS, message = "El segundo nombre debe tener entre 3 y 50 caracteres y solo letras y espacios simples entre palabras")
    private String segundoNombre;

    @Schema(description = "Apellido paterno del cliente", example = "Mendez")
    @NotBlank(message = "El apellido paterno es obligatorio")
    @Size(min = 3, max = 50, message = "El apellido paterno debe tener mínimo 3 y máximo 50 caracteres")
    @Pattern(regexp = NOMBRE_REGEX, message = "El apellido paterno solo debe contener letras y espacios simples entre palabras")
    private String apellidoPaterno;

    @Schema(description = "Apellido materno del cliente", example = "Rodriguez")
    @NotBlank(message = "El apellido materno es obligatorio")
    @Size(min = 3, max = 50, message = "El apellido materno debe tener mínimo 3 y máximo 50 caracteres")
    @Pattern(regexp = NOMBRE_REGEX, message = "El apellido materno solo debe contener letras y espacios simples entre palabras")
    private String apellidoMaterno;

    @Schema(description = "Fecha de nacimiento en formato estrictamente año-mes-día (yyyy-MM-dd)", example = "1995-05-15", type = "string", format = "date")
    @NotNull(message = "La fecha de nacimiento es obligatoria (formato yyyy-MM-dd)")
    @JsonDeserialize(using = FechaEstrictaDeserializer.class)
    @MayorDeEdad
    private LocalDate fechaNacimiento;

    @Schema(description = "CURP a 18 caracteres", example = "MERG950515HMNDRR01")
    @NotBlank(message = "La CURP es obligatoria")
    @Size(min = 18, max = 18, message = "La CURP debe tener exactamente 18 caracteres")
    @Pattern(
            regexp = "^[A-Z]{4}\\d{2}" + MES_DIA + "[HM]" + ENTIDADES + "[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9]\\d$",
            message = "Formato de CURP no válido"
    )
    private String curp;

    @Schema(description = "RFC a 12 o 13 caracteres", example = "MERG950515AB1")
    @NotBlank(message = "El RFC es obligatorio")
    @Size(min = 12, max = 13, message = "El RFC debe tener 12 o 13 caracteres")
    @Pattern(
            regexp = "^[A-ZÑ&]{3,4}\\d{2}" + MES_DIA + "[A-Z0-9]{3}$",
            message = "Formato de RFC no válido"
    )
    private String rfc;

    // Catálogos estrictos: se acepta únicamente el valor exacto (mayúsculas, sin variantes ni sinónimos)

    @Schema(description = "Sexo. Valor exacto del catálogo", example = "MASCULINO", allowableValues = {"MASCULINO", "FEMENINO", "OTRO"})
    @NotBlank(message = "El sexo es obligatorio")
    @Pattern(
            regexp = "^(MASCULINO|FEMENINO|OTRO)$",
            message = "El sexo debe ser exactamente uno de los valores del catálogo: MASCULINO, FEMENINO u OTRO"
    )
    private String sexo;

    @Schema(description = "Nacionalidad. Nombre exacto registrado en cat_nacionalidad (ver GET /api/v1/cat/nacionalidades)", example = "MEXICANA")
    @NotBlank(message = "La nacionalidad es obligatoria")
    @Size(max = 50, message = "La nacionalidad no puede superar los 50 caracteres")
    @Pattern(regexp = "^[A-ZÑ]+( [A-ZÑ]+)*$", message = "La nacionalidad debe enviarse exactamente como el nombre del catálogo, en mayúsculas (ejemplo: MEXICANA)")
    private String nacionalidad;

    @Schema(description = "Estado civil. Valor exacto del catálogo", example = "SOLTERO", allowableValues = {"SOLTERO", "CASADO", "DIVORCIADO", "VIUDO", "UNION LIBRE"})
    @NotBlank(message = "El estado civil es obligatorio")
    @Pattern(
            regexp = "^(SOLTERO|CASADO|DIVORCIADO|VIUDO|UNION LIBRE)$",
            message = "El estado civil debe ser exactamente uno de los valores del catálogo: SOLTERO, CASADO, DIVORCIADO, VIUDO o UNION LIBRE"
    )
    private String estadoCivil;

    @Schema(description = "Identificador biométrico Face ID (opcional)", example = "987654321")
    @Positive(message = "El identificador biométrico debe ser un número positivo")
    private Long datosBiometricos;
}
