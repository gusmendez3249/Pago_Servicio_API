package com.proyecto.servicios.model.gestopago;

import java.math.BigDecimal;

/**
 * Datos que GestoPago devuelve en el XML de una operación aplicada (sendTx con CODIGO 01 o confirmTx con CODIGO 06).
 * El nodo SALDO del XML es el saldo del distribuidor y NUNCA se lee ni se guarda.
 *
 * @param numAutorizacion NUM_AUTORIZACION: es el folio que se muestra al usuario
 * @param pin             PIN de una tarjeta de regalo (secreto: se guarda cifrado y no se registra en logs)
 * @param leyenda         texto informativo del proveedor (solo para el comprobante de un pago exitoso)
 */
public record DatosProveedor(
        String idTx,
        String numAutorizacion,
        BigDecimal comision,
        BigDecimal monto,
        String fecha,
        String referencia,
        String pin,
        String leyenda) {

    /** El PIN nunca debe salir en un log por accidente. */
    @Override
    public String toString() {
        return "DatosProveedor[idTx=" + idTx + ", numAutorizacion=" + numAutorizacion + ", comision=" + comision
                + ", monto=" + monto + ", fecha=" + fecha + ", pin=" + (pin == null ? "null" : "***") + "]";
    }
}
