package com.proyecto.servicios.service;

import com.proyecto.servicios.model.gestopago.ResultadoVerifyReference;
import com.proyecto.servicios.model.gestopago.VerifyReferencePayload;

public interface VerifyReferenceService {

    /**
     * Consulta la referencia en verifyReference de GestoPago. Una consulta no mueve dinero, así que no existe
     * estado indeterminado: cualquier falla se lanza como ProveedorNoDisponibleException
     * (504 si el proveedor no respondió a tiempo, 502 si respondió con error o algo ilegible, 500 si es de configuración).
     */
    ResultadoVerifyReference verificar(VerifyReferencePayload payload);
}
