package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;

import java.util.Optional;

/**
 * Consulta al proveedor (confirmTx) el resultado real de una operación INDETERMINADA. Nunca reenvía sendTx:
 * podría cobrar dos veces.
 */
public interface ConsultaEstadoPagoService {

    /**
     * Reenvía a confirmTx los mismos parámetros del sendTx original (guardados en la operación).
     *
     * @return APROBADA (CODIGO 06, con folio) o RECHAZADA (CODIGO 70); vacío si no se puede determinar todavía
     *         (otro código, timeout, error de red, XML ilegible o una operación sin parámetros guardados)
     */
    Optional<ResultadoSendTx> consultar(TransaccionPagoEntity transaccion);
}
