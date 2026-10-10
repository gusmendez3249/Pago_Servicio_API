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

    // Ya no se usa: se conserva solo para rechazarlo con un mensaje claro en lugar de ignorarlo
    // (ignorarlo en una consulta devolvería todos los clientes). Para 2, 3 y 4 se usa 'filtros'.
    @Schema(hidden = true)
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

    @Schema(description = "Llaves del cliente (curp, rfc, numeroCuenta): obligatorias con el valor completo en las banderas 2 (actualizar) y 3 (baja); opcionales en la bandera 4 (consulta; sin filtros devuelve todos los clientes)")
    @Valid
    private FiltrosClienteDto filtros;
}

