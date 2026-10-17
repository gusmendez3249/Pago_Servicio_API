package com.proyecto.servicios.exception;

import com.proyecto.servicios.controller.onboarding.ClienteLayawayController;
import com.proyecto.servicios.service.onboarding.ClienteLayawayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Manejo centralizado de excepciones: todas las respuestas de error comparten el formato {codigo, mensaje, data}.
 * Los errores de validación de los DTO (@Valid) responden codigo 1 y el resto, codigo = estado HTTP.
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

    private ResultActions enviar(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer x").content(cuerpo));
    }

    // ---------- Validación (@Valid): codigo 1 ----------

    @Test
    void banderaInvalida_Responde400ConCodigo1YDetalleDelCampo() throws Exception {
        enviar("{\"bandera\":9}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(1))
                .andExpect(jsonPath("$.mensaje", startsWith("Datos no validos{bandera=")))
                .andExpect(jsonPath("$.data[0].campo").value("bandera"))
                .andExpect(jsonPath("$.data[0].mensaje", not(emptyString())));
        verifyNoInteractions(servicio);
    }

    @Test
    void banderaAusente_Responde400ConCodigo1() throws Exception {
        enviar("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(1))
                .andExpect(jsonPath("$.data[0].campo").value("bandera"));
    }

    @Test
    void cuerpoInvalido_ElControladorValidaConValidAntesDeLlamarAlServicio() throws Exception {
        enviar("{\"bandera\":2,\"filtros\":{\"curp\":\"PARA950515HDFRMN01\"},\"datosPersonales\":{\"nombre\":\"Li\",\"sexo\":\"masculino\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(1))
                .andExpect(jsonPath("$.mensaje", containsString("datosPersonales.nombre=")))
                .andExpect(jsonPath("$.data[*].campo", hasItems("datosPersonales.nombre", "datosPersonales.sexo")));
        verifyNoInteractions(servicio);
    }

    @Test
    void reglaDeBandera_SeReportaEnElCampoQueLaProvoca() throws Exception {
        enviar("{\"bandera\":3,\"filtros\":{\"curp\":\"PARA95\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(1))
                .andExpect(jsonPath("$.data[0].campo").value("filtros"))
                .andExpect(jsonPath("$.data[0].mensaje", containsString("COMPLETO")));
        verifyNoInteractions(servicio);
    }

    // ---------- Excepciones de negocio: codigo = estado HTTP ----------

    @Test
    void noAutenticado_Responde401ConEncabezadoWwwAuthenticate() throws Exception {
        when(servicio.procesarOperacion(any(), isNull())).thenThrow(new NoAutenticadoException("Se requiere autenticación"));

        mvc.perform(post("/api/v1/layaway/cliente").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(jsonPath("$.codigo").value(401))
                .andExpect(jsonPath("$.mensaje").value("Se requiere autenticación"));
    }

    @Test
    void accesoDenegado_Responde403() throws Exception {
        when(servicio.procesarOperacion(any(), any())).thenThrow(new AccesoDenegadoException("Solo un ejecutivo puede dar de baja clientes."));

        enviar(CUERPO_VALIDO)
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    void clienteInactivo_Responde403() throws Exception {
        when(servicio.procesarOperacion(any(), any())).thenThrow(new ClienteInactivoException("No se puede actualizar la información de un cliente inactivo."));

        enviar(CUERPO_VALIDO).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    void clienteNoEncontrado_Responde404ConMensajeDescriptivo() throws Exception {
        when(servicio.procesarOperacion(any(), any()))
                .thenThrow(new ClienteNoEncontradoException("No se encontró ningún cliente con los filtros indicados."));

        enviar(CUERPO_VALIDO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404))
                .andExpect(jsonPath("$.mensaje", containsString("No se encontró")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void nacionalidadNoValida_Responde404() throws Exception {
        when(servicio.procesarOperacion(any(), any())).thenThrow(new NacionalidadNoValidaException("La nacionalidad 'MARCIANA' no es válida."));

        enviar(CUERPO_VALIDO).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value(404));
    }

    @Test
    void registroDuplicado_Responde409() throws Exception {
        when(servicio.procesarOperacion(any(), any())).thenThrow(new RegistroDuplicadoException("Ya existe un cliente registrado con el correo: a@b.com"));

        enviar(CUERPO_VALIDO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(409))
                .andExpect(jsonPath("$.mensaje", containsString("correo")));
    }

    @Test
    void reglaDeNegocio_Responde400ConCodigo400() throws Exception {
        when(servicio.procesarOperacion(any(), any())).thenThrow(new ReglaNegocioException("La CURP no puede modificarse."));

        enviar(CUERPO_VALIDO).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    void cuentaBloqueada_Responde423() throws Exception {
        when(servicio.procesarOperacion(any(), any())).thenThrow(new CuentaBloqueadaException("Cuenta bloqueada temporalmente."));

        enviar(CUERPO_VALIDO).andExpect(status().isLocked()).andExpect(jsonPath("$.codigo").value(423));
    }

    // ---------- Errores técnicos: sin detalles internos ----------

    @Test
    void errorDeAccesoADatos_Responde500SinExponerElSql() throws Exception {
        when(servicio.procesarOperacion(any(), any()))
                .thenThrow(new InvalidDataAccessApiUsageException("SELECT * FROM tb_cliente WHERE secreto = 1"));

        enviar(CUERPO_VALIDO)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value(500))
                .andExpect(jsonPath("$.mensaje", not(containsString("SELECT"))))
                .andExpect(jsonPath("$.mensaje", containsString("Ocurrió un error")));
    }

    @Test
    void errorNoPrevisto_Responde500DescriptivoSinTraza() throws Exception {
        when(servicio.procesarOperacion(any(), any())).thenThrow(new NullPointerException("campo interno nulo en ClienteEntity"));

        enviar(CUERPO_VALIDO)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value(500))
                .andExpect(jsonPath("$.mensaje", containsString("error inesperado")))
                .andExpect(jsonPath("$.mensaje", not(containsString("ClienteEntity"))));
    }

    @Test
    void illegalArgument_NoDevuelveElMensajeInterno() throws Exception {
        when(servicio.procesarOperacion(any(), any())).thenThrow(new IllegalArgumentException("java.lang.Foo#bar detalle interno"));

        enviar(CUERPO_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje", not(containsString("java.lang"))));
    }

    @Test
    void jsonMalFormado_Responde400() throws Exception {
        enviar("{")
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
