package com.proyecto.servicios.config.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.proyecto.servicios.dto.onboarding.DatosPersonalesDto;
import com.proyecto.servicios.dto.onboarding.DomicilioDto;
import com.proyecto.servicios.dto.onboarding.LoginRequest;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** En los DTO de onboarding un campo de texto solo acepta un valor entre comillas (o null). */
class TextoEstrictoOnboardingConfigTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .registerModule(new TextoEstrictoOnboardingConfig().textoEstrictoOnboardingModule());

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "12345", "1.5"})
    void nombreConValorQueNoEsTexto_SeRechaza(String valor) {
        assertThrows(MismatchedInputException.class,
                () -> mapper.readValue("{\"nombre\":" + valor + "}", DatosPersonalesDto.class));
    }

    @Test
    void codigoPostalNumerico_SeRechaza() {
        assertThrows(MismatchedInputException.class, () -> mapper.readValue("{\"codigoPostal\":37800}", DomicilioDto.class));
    }

    @Test
    void usuarioNumericoEnElLogin_SeRechaza() {
        assertThrows(MismatchedInputException.class,
                () -> mapper.readValue("{\"bandera\":1,\"username\":123,\"password\":\"x\"}", LoginRequest.class));
    }

    @Test
    void textoYNull_SeAceptan() throws Exception {
        DatosPersonalesDto dp = mapper.readValue("{\"nombre\":\"Juan\",\"segundoNombre\":null}", DatosPersonalesDto.class);
        assertEquals("Juan", dp.getNombre());
        assertNull(dp.getSegundoNombre());
    }

    @Test
    void clasesFueraDelOnboarding_ConservanSuComportamiento() throws Exception {
        GestoPagoAuthResponse r = mapper.readValue("{\"message\":200}", GestoPagoAuthResponse.class);
        assertEquals("200", r.getMessage());
    }
}
