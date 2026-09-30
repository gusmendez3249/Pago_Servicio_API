package com.proyecto.servicios.dto.onboarding;

import jakarta.validation.Valid;
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

    @NotNull(message = "La bandera de operación es obligatoria (1 = Insertar, 2 = Actualizar, 3 = Eliminar/Baja Lógica)")
    private Integer bandera;

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
}
