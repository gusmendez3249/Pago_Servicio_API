package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.NoAutenticadoException;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.PersonaService;
import com.proyecto.servicios.service.onboarding.SesionService;
import com.proyecto.servicios.service.onboarding.SesionUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los endpoints heredados de personas (alta, cambio y borrado físico) solo los usa un ejecutivo autenticado. */
class PersonaControllerTest {

    private static final String PERSONA = "{\"nombre\":\"Juan\",\"apellidoP\":\"Perez\",\"apellidoMaterno\":\"Gomez\"}";

    private final PersonaService personaService = mock(PersonaService.class);
    private final SesionService sesionService = mock(SesionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PersonaController controller = new PersonaController();
        ReflectionTestUtils.setField(controller, "personaService", personaService);
        ReflectionTestUtils.setField(controller, "sesionService", sesionService);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(sesionService.autenticar(null)).thenThrow(new NoAutenticadoException("Se requiere autenticación"));
        when(sesionService.autenticar("Bearer cliente")).thenReturn(new SesionUsuario(2L, "juan", "CLIENTE", 10L));
        when(sesionService.autenticar("Bearer ejecutivo")).thenReturn(new SesionUsuario(1L, "admin", "EJECUTIVO", null));
        GenericResponse<Object> ok = GenericResponse.error(0, "ok");
        when(personaService.eliminaPersona(any())).thenReturn(ok);
        when(personaService.actualizaPersona(any())).thenReturn(ok);
    }

    private static MockHttpServletRequestBuilder[] peticiones(String authorization) {
        MockHttpServletRequestBuilder[] p = {
                post("/personas").content(PERSONA),
                put("/personasActualiza").content(PERSONA),
                put("/personasElimina").content("{\"nombre\":\"Juan\"}")};
        for (MockHttpServletRequestBuilder r : p) {
            r.contentType(MediaType.APPLICATION_JSON);
            if (authorization != null) {
                r.header("Authorization", authorization);
            }
        }
        return p;
    }

    @Test
    void sinSesion_401YNoTocaDatos() throws Exception {
        for (MockHttpServletRequestBuilder r : peticiones(null)) {
            mvc.perform(r).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(personaService);
    }

    @Test
    void cliente_403YNoTocaDatos() throws Exception {
        for (MockHttpServletRequestBuilder r : peticiones("Bearer cliente")) {
            mvc.perform(r).andExpect(status().isForbidden());
        }
        verifyNoInteractions(personaService);
    }

    @Test
    void ejecutivo_PuedeActualizarYEliminar() throws Exception {
        MockHttpServletRequestBuilder[] p = peticiones("Bearer ejecutivo");
        mvc.perform(p[1]).andExpect(status().isOk());
        mvc.perform(p[2]).andExpect(status().isOk());
        verify(personaService).actualizaPersona(any());
        verify(personaService).eliminaPersona(any());
    }
}
