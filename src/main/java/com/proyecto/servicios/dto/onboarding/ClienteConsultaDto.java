package com.proyecto.servicios.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Cliente devuelto por la consulta (bandera 4). No incluye datos biométricos ni credenciales. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteConsultaDto {

    private String nombreCompleto;
    private String curp;
    private String rfc;
    private String correo;
    private String telefonoMovil;
    private Boolean activo;
    private LocalDateTime fechaRegistro;
    private List<CuentaConsultaDto> cuentas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CuentaConsultaDto {
        private String numeroCuenta;
        private BigDecimal saldo;
        private String estatus;
    }
}
