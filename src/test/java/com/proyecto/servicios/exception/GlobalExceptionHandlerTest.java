package com.proyecto.servicios.exception;

import com.proyecto.servicios.controller.onboarding.ClienteLayawayController;
import com.proyecto.servicios.service.onboarding.ClienteLayawayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Manejo centralizado de excepciones: todas las respuestas de error comparten el mismo formato
 * ({codigo, mensaje, data}) y el código coincide con el estado HTTP.
 */
class GlobalExceptionHandlerTest {

    private ClienteLayawayService servicio;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        servicio = mock(ClienteLayawayService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ClienteLayawayController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static final String CUERPO_VALIDO = "{\"bandera\":4}";

    @Test
    void banderaInvalida_Responde400ConCodigoCoherenteYDetalleDelCampo() throws Exception {
        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content("{\"bandera\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje", startsWith("Datos no validos")))
                .andExpect(jsonPath("$.data[0].campo").value("bandera"))
                .andExpect(jsonPath("$.data[0].mensaje", not(emptyString())));
    }

    @Test
    void banderaAusente_Responde400() throws Exception {
        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.data[0].campo").value("bandera"));
    }

    @Test
    void validacionDelCuerpo_HechaEnElServicio_TieneElMismoFormato() throws Exception {
        java.util.Map<String, String> errores = new java.util.TreeMap<>();
        errores.put("datosPersonales.nombre", "El nombre debe tener entre 3 y 50 caracteres");
        errores.put("datosPersonales.sexo", "El sexo debe ser exactamente uno de los valores del catálogo");
        when(servicio.procesarOperacion(any(), any())).thenThrow(new ValidacionException(errores));

        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer x").content(CUERPO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje", startsWith("Datos no validos")))
                .andExpect(jsonPath("$.data[*].campo", hasItems("datosPersonales.nombre", "datosPersonales.sexo")))
                .andExpect(jsonPath("$.data[0].mensaje", not(emptyString())));
    }

    @Test
    void noAutenticado_Responde401ConEncabezadoWwwAuthenticate() throws Exception {
        when(servicio.procesarOperacion(any(), isNull()))
                .thenThrow(new OnboardingException("Se requiere autenticación", HttpStatus.UNAUTHORIZED, 401));

        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(jsonPath("$.codigo").value(401))
                .andExpect(jsonPath("$.mensaje").value("Se requiere autenticación"));
    }

    @Test
    void sinPermiso_Responde403() throws Exception {
        when(servicio.procesarOperacion(any(), any()))
                .thenThrow(new OnboardingException("Solo un ejecutivo puede dar de baja clientes.", HttpStatus.FORBIDDEN, 403));

        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer x").content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    void registroInexistente_Responde404ConMensajeDescriptivo() throws Exception {
        when(servicio.procesarOperacion(any(), any()))
                .thenThrow(new OnboardingException("No se encontró ningún cliente con los filtros indicados.", HttpStatus.NOT_FOUND, 404));

        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer x").content(CUERPO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404))
                .andExpect(jsonPath("$.mensaje", containsString("No se encontró")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void errorDeAccesoADatos_Responde500SinExponerElSql() throws Exception {
        when(servicio.procesarOperacion(any(), any()))
                .thenThrow(new InvalidDataAccessApiUsageException("SELECT * FROM tb_cliente WHERE secreto = 1"));

        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer x").content(CUERPO_VALIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value(500))
                .andExpect(jsonPath("$.mensaje", not(containsString("SELECT"))))
                .andExpect(jsonPath("$.mensaje", containsString("Ocurrió un error")));
    }

    @Test
    void jsonMalFormado_Responde400() throws Exception {
        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    void contentTypeIncorrecto_Responde415() throws Exception {
        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.TEXT_PLAIN).content(CUERPO_VALIDO))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value(415));
    }
}
