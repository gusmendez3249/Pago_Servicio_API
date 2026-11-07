package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * JSON de la petición sendTx (antes de cifrarse). Es inmutable: una vez armado no se puede alterar
 * antes de serializarlo y cifrarlo. Los campos nulos (telefono o referencia) no se envían.
 *
 * Se crea solo con {@link #porTelefono} o {@link #porReferencia}: el constructor exige los campos obligatorios,
 * así que un payload sin monto, producto o UPC no llega a construirse.
 *
 * @param montoPago BigDecimal con escala 2: un double podría alterar el monto (p. ej. 0.1 + 0.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendTxPayload(
        Integer idProducto,
        Integer idServicio,
        String telefono,
        String referencia,
        BigDecimal montoPago,
        String horaLocal,
        String upc,
        String unidad) {

    public SendTxPayload {
        Objects.requireNonNull(idProducto, "idProducto es obligatorio");
        Objects.requireNonNull(idServicio, "idServicio es obligatorio");
        Objects.requireNonNull(montoPago, "montoPago es obligatorio");
        Objects.requireNonNull(horaLocal, "horaLocal es obligatorio");
        Objects.requireNonNull(upc, "upc es obligatorio");
        // unidad es opcional: si no está configurada (gestopago.sendtx.unidad) no se envía
        if (telefono == null && referencia == null) {
            throw new IllegalArgumentException("Se requiere telefono o referencia");
        }
    }

    /** Pago por número telefónico (recargas): no lleva referencia. */
    public static SendTxPayload porTelefono(Integer idProducto, Integer idServicio, String telefono,
                                            BigDecimal montoPago, String horaLocal, String upc, String unidad) {
        return new SendTxPayload(idProducto, idServicio, telefono, null, montoPago, horaLocal, upc, unidad);
    }

    /**
     * Pago por referencia (servicios). {@code telefono} es el valor fijo que GestoPago exige según el producto,
     * o {@code null} si el producto no lo requiere.
     */
    public static SendTxPayload porReferencia(Integer idProducto, Integer idServicio, String referencia,
                                              String telefono, BigDecimal montoPago, String horaLocal,
                                              String upc, String unidad) {
        return new SendTxPayload(idProducto, idServicio, telefono, referencia, montoPago, horaLocal, upc, unidad);
    }
}
