package com.proyecto.servicios.model.gestopago;

import java.util.Objects;

/** JSON de la petición verifyReference (antes de cifrarse). */
public record VerifyReferencePayload(Integer idProducto, Integer idServicio, String referencia) {

    public VerifyReferencePayload {
        Objects.requireNonNull(idProducto, "idProducto es obligatorio");
        Objects.requireNonNull(idServicio, "idServicio es obligatorio");
        Objects.requireNonNull(referencia, "referencia es obligatoria");
    }

    /** La referencia es un dato del cliente: no se imprime en logs. */
    @Override
    public String toString() {
        return "VerifyReferencePayload[idProducto=" + idProducto + ", idServicio=" + idServicio + "]";
    }
}
