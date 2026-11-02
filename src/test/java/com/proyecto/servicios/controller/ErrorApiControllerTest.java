package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ErrorApiControllerTest {

    private static ResponseEntity<GenericResponse<Void>> error(Integer codigo) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        if (codigo != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, codigo);
        }
        return new ErrorApiController().error(request);
    }

    @Test
    void visitaDirectaAError_Responde404() {
        ResponseEntity<GenericResponse<Void>> respuesta = error(null);
        assertEquals(404, respuesta.getStatusCode().value());
        assertEquals(404, respuesta.getBody().getCodigo());
    }

    @Test
    void errorInterno_RespondeMensajeGenericoSinDetalles() {
        ResponseEntity<GenericResponse<Void>> respuesta = error(500);
        assertEquals(500, respuesta.getStatusCode().value());
        assertEquals("Ocurrió un error interno. Intente más tarde.", respuesta.getBody().getMensaje());
        assertNull(respuesta.getBody().getData());
    }

    @Test
    void conservaOtrosCodigosDeError() {
        assertEquals(400, error(400).getStatusCode().value());
    }
}
