package com.proyecto.servicios.model.gestopago;

import com.proyecto.servicios.enums.ErrorCode;

/**
 * Desenlace de un envío a sendTx (o de una consulta a confirmTx), clasificado según lo que se sabe con certeza del
 * lado del proveedor.
 *
 * @param codigo nodo CODIGO del XML (nulo si no hubo respuesta legible)
 * @param texto  nodo TEXTO del XML (solo para registro interno; no se devuelve tal cual al cliente)
 * @param error  causa técnica cuando el desenlace es NO_ENVIADA o INDETERMINADA
 * @param datos  folio, id de transacción, comisión, PIN, etc. de una operación aplicada (nulo en los demás desenlaces)
 */
public record ResultadoSendTx(Desenlace desenlace, String codigo, String texto, ErrorCode error, DatosProveedor datos) {

    public enum Desenlace {
        /** CODIGO 01 (sendTx) o 06 (confirmTx): el proveedor aplicó el pago. */
        APROBADA,
        /** El proveedor respondió con un código de rechazo definitivo: el pago no se aplicó. */
        RECHAZADA,
        /** Hay certeza de que la petición no llegó a procesarse (fallo de conexión, cifrado, credenciales, 4xx). */
        NO_ENVIADA,
        /** La petición pudo haberse procesado (timeout de lectura, 5xx, respuesta ilegible, código ambiguo). */
        INDETERMINADA
    }

    public static ResultadoSendTx aprobada(String codigo, String texto) {
        return aprobada(codigo, texto, null);
    }

    public static ResultadoSendTx aprobada(String codigo, String texto, DatosProveedor datos) {
        return new ResultadoSendTx(Desenlace.APROBADA, codigo, texto, null, datos);
    }

    public static ResultadoSendTx rechazada(String codigo, String texto) {
        return new ResultadoSendTx(Desenlace.RECHAZADA, codigo, texto, null, null);
    }

    public static ResultadoSendTx noEnviada(ErrorCode error) {
        return new ResultadoSendTx(Desenlace.NO_ENVIADA, null, null, error, null);
    }

    public static ResultadoSendTx indeterminada(String codigo, String texto, ErrorCode error) {
        return new ResultadoSendTx(Desenlace.INDETERMINADA, codigo, texto, error, null);
    }
}
