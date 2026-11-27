package com.proyecto.servicios.validation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Deserializa fechas yyyy-MM-dd en modo ESTRICTO. Con el formateador por defecto (modo SMART)
 * una fecha inexistente como 1995-02-31 se "corrige" en silencio a 1995-02-28 y se acepta.
 */
public class FechaEstrictaDeserializer extends StdDeserializer<LocalDate> {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    public FechaEstrictaDeserializer() {
        super(LocalDate.class);
    }

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() != JsonToken.VALUE_STRING) {
            return (LocalDate) ctxt.handleUnexpectedToken(LocalDate.class, p);
        }
        String texto = p.getText();
        try {
            return LocalDate.parse(texto, FORMATO);
        } catch (DateTimeParseException e) {
            return (LocalDate) ctxt.handleWeirdStringValue(LocalDate.class, texto,
                    "La fecha no existe o no tiene el formato yyyy-MM-dd");
        }
    }
}
