package com.proyecto.servicios.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayawayClienteRequest {

    @Schema(description = "Bandera de operación (1 = Insertar, 2 = Actualizar, 3 = Eliminar/Baja Lógica, 4 = Consultar)", example = "1")
    @NotNull(message = "La bandera de operación es obligatoria (1 = Insertar, 2 = Actualizar, 3 = Eliminar/Baja Lógica, 4 = Consultar)")
    @Min(value = 1, message = "La bandera de operación solo permite los enteros 1 (Insertar), 2 (Actualizar), 3 (Eliminar) o 4 (Consultar)")
    @Max(value = 4, message = "La bandera de operación solo permite los enteros 1 (Insertar), 2 (Actualizar), 3 (Eliminar) o 4 (Consultar)")
    private Integer bandera;

    @Schema(description = "ID de cliente (obligatorio para banderas 2 y 3)", example = "1")
    private Long clienteId;

    @Valid
    private DatosPersonalesDto datosPersonales;

    @Valid
    private DatosContactoDto datosContacto;

    @Valid
    private DomicilioDto domicilio;

    @Valid
    private InformacionLaboralDto informacionLaboral;

    @Valid
    private LoginCredencialesDto loginCredenciales;

    @Schema(description = "Solo para bandera 4 (consulta). Opcional: sin filtros se devuelven todos los clientes")
    @Valid
    private FiltrosClienteDto filtros;
}

