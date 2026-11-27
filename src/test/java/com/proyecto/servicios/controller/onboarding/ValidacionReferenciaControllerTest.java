package com.proyecto.servicios.controller.onboarding;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.JacksonConfig;
import com.proyecto.servicios.config.onboarding.TextoEstrictoOnboardingConfig;
import com.proyecto.servicios.dto.onboarding.ValidarReferenciaResponse;
import com.proyecto.servicios.enums.EstadoValidacionReferencia;
import com.proyecto.servicios.enums.MotivoValidacionReferencia;
import com.proyecto.servicios.enums.SituacionSaldoReferencia;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.NoAutenticadoException;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.service.onboarding.ValidacionReferenciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contrato HTTP de POST /api/v1/pagos/referencia/validar con la misma configuración de Jackson de la aplicación. */
class ValidacionReferenciaControllerTest {

    private static final String URL = "/api/v1/pagos/referencia/validar";
    private static final String VALIDO = "{\"idSer\":1,\"idProd\":2,\"referencia\":\"0012345678\"}";

    private final ValidacionReferenciaService servicio = mock(ValidacionReferenciaService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().disableCoercionCustomizer().customize(builder);
        builder.featuresToDisable(MapperFeature.ALLOW_COERCION_OF_SCALARS, DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        builder.modulesToInstall(new TextoEstrictoOnboardingConfig().textoEstrictoOnboardingModule());
        ObjectMapper mapper = builder.build();

        mockMvc = MockMvcBuilders.standaloneSetup(new ValidacionReferenciaController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private ResultActions validar(String cuerpo) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer token").content(cuerpo));
    }

    @Test
    void referenciaValida_200ConContrato() throws Exception {
        when(servicio.validar(eq("Bearer token"), any())).thenReturn(ValidarReferenciaResponse.builder()
                .estado(EstadoValidacionReferencia.VALIDA).situacion(SituacionSaldoReferencia.ADEUDO)
                .saldo(new BigDecimal("350.00")).idValidacion("3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c")
                .vigenciaSegundos(300).puedePagar(true).idSer(1).idProd(2).referencia("0012345678")
                .servicio("CFE").producto("Recibo").build());

        validar(VALIDO).andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.data.estado").value("VALIDA"))
                .andExpect(jsonPath("$.data.situacion").value("ADEUDO"))
                .andExpect(jsonPath("$.data.saldo").value(350.00))
                .andExpect(jsonPath("$.data.idValidacion").value("3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c"))
                .andExpect(jsonPath("$.data.vigenciaSegundos").value(300))
                .andExpect(jsonPath("$.data.puedePagar").value(true));
    }

    @Test
    void referenciaInvalida_200ConEnumeradosYNulos() throws Exception {
        when(servicio.validar(any(), any())).thenReturn(ValidarReferenciaResponse.builder()
                .estado(EstadoValidacionReferencia.INVALIDA).motivo(MotivoValidacionReferencia.REFERENCIA_INVALIDA)
                .puedePagar(false).idSer(1).idProd(2).referencia("0012345678").build());

        validar(VALIDO).andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.data.estado").value("INVALIDA"))
                .andExpect(jsonPath("$.data.motivo").value("REFERENCIA_INVALIDA"))
                .andExpect(jsonPath("$.data.idValidacion").doesNotExist())
                .andExpect(jsonPath("$.data.puedePagar").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"idSer\":1,\"idProd\":2,\"referencia\":\"0012345678\",\"saldo\":1}",
            "{\"idSer\":1,\"idProd\":2}",
            "{\"idSer\":1,\"referencia\":\"ABC\"}",
            "{\"idSer\":0,\"idProd\":2,\"referencia\":\"ABC\"}",
            "{\"idSer\":1000000,\"idProd\":2,\"referencia\":\"ABC\"}",
            "{\"idSer\":\"1\",\"idProd\":2,\"referencia\":\"ABC\"}",
            "{\"idSer\":1,\"idProd\":2,\"referencia\":\"AB C\"}",
            "{\"idSer\":1,\"idProd\":2,\"referencia\":\"\"}",
            "{\"idSer\":1,\"idProd\":2,\"referencia\":12345}",
            "{\"idSer\":1,\"idProd\":2,\"referencia\":\"AB'--\"}",
            "no es json", "", "[]"
    })
    void cuerpoInvalido_400SinLlegarAlServicio(String cuerpo) throws Exception {
        validar(cuerpo).andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void referenciaDe101Caracteres_400() throws Exception {
        validar("{\"idSer\":1,\"idProd\":2,\"referencia\":\"" + "A".repeat(101) + "\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void validacion_Codigo1ConCampo() throws Exception {
        validar("{\"idSer\":1,\"idProd\":2,\"referencia\":\"A B\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(1))
                .andExpect(jsonPath("$.data[0].campo").value("referencia"));
    }

    @Test
    void sinSesion_401() throws Exception {
        when(servicio.validar(any(), any())).thenThrow(new NoAutenticadoException("sin token"));
        validar(VALIDO).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value(401));
    }

    @Test
    void ejecutivo_403() throws Exception {
        when(servicio.validar(any(), any())).thenThrow(new AccesoDenegadoException("no"));
        validar(VALIDO).andExpect(status().isForbidden());
    }

    @Test
    void productoNoVerificable_400() throws Exception {
        when(servicio.validar(any(), any())).thenThrow(new ReglaNegocioException("no admite"));
        validar(VALIDO).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    void productoInexistente_404() throws Exception {
        when(servicio.validar(any(), any())).thenThrow(new RecursoNoEncontradoException("no existe"));
        validar(VALIDO).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void fallasDelProveedorYAlmacen_MismoCodigoHttp(int codigo) throws Exception {
        when(servicio.validar(any(), any())).thenThrow(new ProveedorNoDisponibleException("falla", HttpStatus.valueOf(codigo)));
        validar(VALIDO).andExpect(status().is(codigo)).andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
