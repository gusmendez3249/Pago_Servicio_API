package com.proyecto.servicios.exception;

/** Ningún cliente coincide con las llaves o filtros indicados entre los registros accesibles. Se responde 404. */
public class ClienteNoEncontradoException extends RecursoNoEncontradoException {

    public ClienteNoEncontradoException(String message) {
        super(message);
    }
}
