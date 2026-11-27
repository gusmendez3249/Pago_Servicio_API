package com.proyecto.servicios.model.gestopago;

import java.math.BigDecimal;

/**
 * Resultado de verifyReference. Las fallas del proveedor no son un resultado: se lanzan como excepción.
 *
 * @param valida CODIGO 01 (referencia existente)
 * @param saldo  SALDO numérico del XML (solo si es válida): positivo adeudo, cero sin adeudo, negativo saldo a favor
 * @param texto  nodo TEXTO del XML (solo para registro interno; jamás se devuelve al cliente)
 */
public record ResultadoVerifyReference(boolean valida, BigDecimal saldo, String texto) {

    public static ResultadoVerifyReference valida(BigDecimal saldo, String texto) {
        return new ResultadoVerifyReference(true, saldo, texto);
    }

    public static ResultadoVerifyReference invalida(String texto) {
        return new ResultadoVerifyReference(false, null, texto);
    }
}
