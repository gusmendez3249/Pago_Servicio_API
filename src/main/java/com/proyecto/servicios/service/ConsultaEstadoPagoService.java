package com.proyecto.servicios.service;

import com.proyecto.servicios.model.gestopago.ResultadoSendTx;

import java.util.Optional;

/**
 * Consulta al proveedor el resultado real de una operación por su upc. Es el punto de extensión de la
 * conciliación automática: cuando GestoPago proporcione su servicio de consulta de transacciones se implementa aquí.
 */
public interface ConsultaEstadoPagoService {

    /** @return el desenlace confirmado por el proveedor, o vacío si no se puede determinar todavía */
    Optional<ResultadoSendTx> consultar(String upc);
}
