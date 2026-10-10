package com.proyecto.servicios.dto.onboarding;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Los campos nulos no se envían: la consulta (bandera 4) no trae los datos de un solo cliente y
// las demás operaciones no traen la lista de clientes
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    // Solo se llenan en la consulta (bandera 4)
    private List<ClienteConsultaDto> clientes;
    private Long totalCoincidencias;
    private Boolean resultadosTruncados;
}
