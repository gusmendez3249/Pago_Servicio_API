package com.proyecto.servicios.service;

import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.model.gestopago.SendTxPayload;

public interface SendTxService {

    /**
     * Cifra el payload, lo envía a sendTx con el token Bearer vigente y clasifica el desenlace.
     * Nunca lanza excepción por fallas del proveedor: toda falla se traduce a NO_ENVIADA o INDETERMINADA
     * para que quien llama decida si reintegra el saldo o lo retiene hasta conciliar.
     */
    ResultadoSendTx enviar(SendTxPayload payload);
}
