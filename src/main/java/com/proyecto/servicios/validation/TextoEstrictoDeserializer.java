package com.proyecto.servicios.validation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Acepta únicamente un valor JSON de tipo texto (entre comillas) o null.
 * Por defecto Jackson convierte un número o un booleano en String ({@code 123} -> {@code "123"});
 * aquí se rechaza para que el cliente siempre envíe {@code "123"}.
 */
public class TextoEstrictoDeserializer extends StdDeserializer<String> {

    public TextoEstrictoDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            return p.getText();
        }
        return (String) ctxt.handleUnexpectedToken(String.class, p);
    }
}
