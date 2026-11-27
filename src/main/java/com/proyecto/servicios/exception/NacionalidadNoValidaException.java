package com.proyecto.servicios.exception;

/** La nacionalidad no existe (o no está activa) en el catálogo cat_nacionalidad. Se responde 404. */
public class NacionalidadNoValidaException extends RecursoNoEncontradoException {

    public NacionalidadNoValidaException(String message) {
        super(message);
    }
}
