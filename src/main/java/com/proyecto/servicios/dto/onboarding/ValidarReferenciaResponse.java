package com.proyecto.servicios.dto.onboarding;

import com.proyecto.servicios.enums.EstadoValidacionReferencia;
import com.proyecto.servicios.enums.MotivoValidacionReferencia;
import com.proyecto.servicios.enums.SituacionSaldoReferencia;
import com.proyecto.servicios.enums.VerificacionReferencia;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Resultado de validar una referencia. Solo enumerados y datos del catálogo: nunca texto del proveedor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidarReferenciaResponse {

    @Schema(description = "VALIDA o INVALIDA")
    private EstadoValidacionReferencia estado;

    @Schema(description = "Solo si es INVALIDA: REFERENCIA_INVALIDA")
    private MotivoValidacionReferencia motivo;

    @Schema(description = "EN_LINEA (consultada con el proveedor) o SOLO_FORMATO (el proveedor no ofrece consulta para este "
            + "servicio: solo se comprobó el formato). En INVALIDA siempre EN_LINEA")
    private VerificacionReferencia verificacion;

    @Schema(description = "Total que debe pagarse (el saldo con adeudo) cuando la verificación fue EN_LINEA y puede pagar; "
            + "null en SOLO_FORMATO (el monto lo captura el usuario de su recibo)", example = "350.00")
    private BigDecimal montoAPagar;

    @Schema(description = "Solo si es VALIDA: ADEUDO (saldo > 0), SIN_ADEUDO (= 0) o SALDO_A_FAVOR (< 0)")
    private SituacionSaldoReferencia situacion;

    @Schema(description = "Solo si es VALIDA: saldo de la referencia (negativo = a favor)", example = "350.00")
    private BigDecimal saldo;

    @Schema(description = "Comprobante (UUID v4) que debe enviarse como idValidacion en el pago. Solo si es VALIDA y el "
            + "producto admite pago; de un solo uso", example = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c")
    private String idValidacion;

    @Schema(description = "Segundos de vigencia del comprobante", example = "300")
    private Integer vigenciaSegundos;

    @Schema(description = "true si el producto admite pago (false = solo consulta de saldo)")
    private boolean puedePagar;

    private Integer idSer;
    private Integer idProd;
    private String referencia;
    private String servicio;
    private String producto;
}
