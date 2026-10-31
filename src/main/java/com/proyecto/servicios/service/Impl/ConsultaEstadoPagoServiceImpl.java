package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.service.ConsultaEstadoPagoService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * No se cuenta con la especificación de un servicio de consulta de transacciones de GestoPago, así que no se
 * inventa uno: el resultado se reporta como "no determinable" y la operación queda INDETERMINADA (monto retenido)
 * hasta que un ejecutivo la concilie con el reporte del proveedor. Reenviar sendTx para "averiguar" el resultado
 * NO es una opción: podría cobrar dos veces.
 */
@Service
public class ConsultaEstadoPagoServiceImpl implements ConsultaEstadoPagoService {

    @Override
    public Optional<ResultadoSendTx> consultar(String upc) {
        return Optional.empty();
    }
}
