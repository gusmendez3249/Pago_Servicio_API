package com.proyecto.servicios.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayawayClienteResponse {

    private Long clienteId;
    private String nombreCompleto;
    private String curp;
    private String rfc;
    private String correo;
    private Boolean activo;
    private String numeroCuenta;
    private BigDecimal saldoInicial;
    private String estatusCuenta;
    private String username;
    private String operacionRealizada;
    private LocalDateTime fechaOperacion;
}
