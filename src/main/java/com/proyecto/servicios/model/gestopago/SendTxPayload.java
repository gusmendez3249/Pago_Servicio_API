package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** JSON de la petición sendTx (antes de cifrarse). Los campos nulos (telefono o referencia) no se envían. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendTxPayload {

    private Integer idProducto;
    private Integer idServicio;
    private String telefono;
    private String referencia;
    /** BigDecimal con escala 2: un double podría alterar el monto (p. ej. 0.1 + 0.2). */
    private BigDecimal montoPago;
    private String horaLocal;
    private String upc;
    private String unidad;
}
