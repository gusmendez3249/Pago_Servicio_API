package com.proyecto.servicios.dto.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de los filtros de la consulta (bandera 4): comillas, no en blanco y solo campos llave. */
class FiltrosClienteDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private Set<String> camposInvalidos(String json) throws Exception {
        FiltrosClienteDto dto = mapper.readValue(json, FiltrosClienteDto.class);
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    // ---------- Sin filtros: válido (devuelve todos los clientes) ----------

    @Test
    void sinFiltros_EsValidoYEstaVacio() throws Exception {
        FiltrosClienteDto dto = mapper.readValue("{}", FiltrosClienteDto.class);
        assertTrue(dto.estaVacio());
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void camposNulosExplicitos_CuentanComoSinFiltro() throws Exception {
        FiltrosClienteDto dto = mapper.readValue("{\"curp\": null, \"rfc\": null, \"numeroCuenta\": null, \"clienteId\": null}", FiltrosClienteDto.class);
        assertTrue(dto.estaVacio());
    }

    // ---------- Texto entre comillas ----------

    @ParameterizedTest
    @ValueSource(strings = {"{\"curp\": 123}", "{\"curp\": true}", "{\"rfc\": 12.5}", "{\"numeroCuenta\": 3651}",
            "{\"numeroCuenta\": false}", "{\"curp\": [\"MERG\"]}", "{\"rfc\": {\"x\": 1}}"})
    void valorSinComillas_SeRechaza(String json) {
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(json, FiltrosClienteDto.class), json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"curp\": \"MERG95\"}", "{\"rfc\": \"merg9505\"}", "{\"numeroCuenta\": \"3651\"}",
            "{\"curp\": \"M\"}", "{\"clienteId\": 5}", "{\"curp\": \"MERG\", \"rfc\": \"AB1\", \"numeroCuenta\": \"12\", \"clienteId\": 1}"})
    void valoresValidosEntreComillas_SeAceptan(String json) throws Exception {
        assertTrue(camposInvalidos(json).isEmpty(), json);
    }

    // ---------- No en blanco ----------

    @ParameterizedTest
    @ValueSource(strings = {"{\"curp\": \"\"}", "{\"curp\": \"   \"}", "{\"rfc\": \"\"}", "{\"rfc\": \" \"}",
            "{\"numeroCuenta\": \"\"}", "{\"numeroCuenta\": \"  \"}"})
    void textoEnBlanco_SeRechaza(String json) throws Exception {
        assertFalse(camposInvalidos(json).isEmpty(), json);
    }

    // ---------- Caracteres que no deben llegar a la consulta (comodines LIKE, espacios, inyección) ----------

    @ParameterizedTest
    @ValueSource(strings = {"{\"curp\": \"%\"}", "{\"curp\": \"_\"}", "{\"curp\": \"AB CD\"}", "{\"curp\": \"AB'--\"}",
            "{\"rfc\": \"%%\"}", "{\"numeroCuenta\": \"12AB\"}", "{\"numeroCuenta\": \"-1\"}",
            "{\"curp\": \"MERG950515HGTNDS091\"}", "{\"numeroCuenta\": \"12345678901234\"}", "{\"curp\": \"MERG\\n\"}"})
    void caracteresOLongitudInvalidos_SeRechazan(String json) throws Exception {
        assertFalse(camposInvalidos(json).isEmpty(), json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"clienteId\": 0}", "{\"clienteId\": -3}"})
    void clienteIdNoPositivo_SeRechaza(String json) throws Exception {
        assertTrue(camposInvalidos(json).contains("clienteId"), json);
    }

    // ---------- Solo parámetros llave que no cambian ----------

    @ParameterizedTest
    @ValueSource(strings = {"{\"nombre\": \"f\"}", "{\"correo\": \"a@b.com\"}", "{\"activo\": true}",
            "{\"telefonoMovil\": \"4181234567\"}", "{\"curp\": \"MERG\", \"apellidoPaterno\": \"x\"}"})
    void camposQueNoSonLlave_SeRechazanEnLugarDeDevolverTodo(String json) throws Exception {
        assertTrue(camposInvalidos(json).contains("sinCamposDesconocidos"), json);
    }
}
