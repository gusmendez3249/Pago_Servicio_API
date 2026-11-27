package com.proyecto.servicios.service.onboarding;

import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;

/** Almacén alterno de comprobantes de validación de referencia, usado cuando Redis no responde. */
public interface RespaldoComprobantes {

    void guardar(String idValidacion, String valor, Duration vigencia);

    /** Devuelve y elimina el comprobante vigente (una sola vez); nulo si no existe o venció. */
    String consumir(String idValidacion);

    /** Sin respaldo: cualquier operación falla como almacén no disponible. */
    static RespaldoComprobantes ninguno() {
        return new RespaldoComprobantes() {
            @Override
            public void guardar(String idValidacion, String valor, Duration vigencia) {
                throw new DataAccessResourceFailureException("Sin almacén de respaldo");
            }

            @Override
            public String consumir(String idValidacion) {
                throw new DataAccessResourceFailureException("Sin almacén de respaldo");
            }
        };
    }
}
