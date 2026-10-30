package com.proyecto.servicios.controller.onboarding;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.JacksonConfig;
import com.proyecto.servicios.config.onboarding.TextoEstrictoOnboardingConfig;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.NoAutenticadoException;
import com.proyecto.servicios.exception.OperacionDuplicadaException;
import com.proyecto.servicios.exception.PagoRechazadoException;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.service.onboarding.ConciliacionPagoService;
import com.proyecto.servicios.service.onboarding.PagoServicioService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integración HTTP del endpoint: deserialización estricta (misma configuración de Jackson que la aplicación),
 * Bean Validation, @RestControllerAdvice y códigos de estado de cada escenario.
 */
class PagoServicioControllerIntegrationTest {

    private static final String URL = "/api/v1/pagos/servicio";
    private static final String LLAVE = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c";
    private static final String VALIDO = """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,
             "telefono":"4181234567","twoFA":"1234"}""";

    private final PagoServicioService pagoServicioService = mock(PagoServicioService.class);
    private final ConciliacionPagoService conciliacionPagoService = mock(ConciliacionPagoService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().disableCoercionCustomizer().customize(builder);
        builder.featuresToDisable(MapperFeature.ALLOW_COERCION_OF_SCALARS, DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        builder.modulesToInstall(new TextoEstrictoOnboardingConfig().textoEstrictoOnboardingModule());
        ObjectMapper mapper = builder.build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PagoServicioController(pagoServicioService, conciliacionPagoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private ResultActions pagar(String cuerpo) throws Exception {
        return mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer token")
                .header("Idempotency-Key", LLAVE)
                .content(cuerpo));
    }

    private static PagoServicioResponse respuesta(EstatusTransaccionPago estatus) {
        return PagoServicioResponse.builder().numCuenta("********1188").upc("ABCDE12345").estatus(estatus)
                .monto(new BigDecimal("100.50")).build();
    }

    // ---------------------------------------------------------------- éxito

    @Test
    void pagoConfirmado_200() throws Exception {
        when(pagoServicioService.procesarPago(eq("Bearer token"), eq(LLAVE), any()))
                .thenReturn(respuesta(EstatusTransaccionPago.CONFIRMADA));

        pagar(VALIDO).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estatus").value("CONFIRMADA"))
                .andExpect(jsonPath("$.data.upc").value("ABCDE12345"))
                .andExpect(content().string(not(containsString("1234\""))));
    }

    @Test
    void pagoIndeterminado_202() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenReturn(respuesta(EstatusTransaccionPago.INDETERMINADA));

        pagar(VALIDO).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.estatus").value("INDETERMINADA"));
    }

    // ---------------------------------------------------------------- entrada estricta (400 sin llegar al servicio)

    @ParameterizedTest
    @ValueSource(strings = {
            // campos extra
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"1234","saldo":1}""",
            // numCuenta: longitud, letras, número
            """
            {"numCuenta":"69542904118","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":"69542904118A","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":695429041188,"idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            // ids: texto, decimal, cero, negativo
            """
            {"numCuenta":"695429041188","idSer":"1","idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1.5,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":0,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":-1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            // monto: texto, 3 decimales, cero, negativo, excesivo, ausente
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":"100.50","esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.505,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":0,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":-5,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100000.00,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"esReferencia":false,"telefono":"4181234567","twoFA":"1234"}""",
            // bandera como texto
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":"false","telefono":"4181234567","twoFA":"1234"}""",
            // teléfono: corto, letras, número, ausente, junto con referencia
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"418123456","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"41812345AB","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":4181234567,"twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","reference":"ABC","twoFA":"1234"}""",
            // referencia: con espacios, vacía, con símbolos
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":true,"reference":"001 234","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":true,"reference":"","twoFA":"1234"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":true,"reference":"0012'--","twoFA":"1234"}""",
            // PIN: 5 dígitos, letras, número, ausente
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"12345"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":"12a4"}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567","twoFA":1234}""",
            """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,"telefono":"4181234567"}""",
            // cuerpo no JSON / vacío / arreglo
            "no es json", "", "[]"
    })
    void entradaInvalida_400SinProcesar(String cuerpo) throws Exception {
        pagar(cuerpo).andExpect(status().isBadRequest());
        verifyNoInteractions(pagoServicioService);
    }

    @Test
    void campoNoPermitido_SeNombraEnLaRespuesta() throws Exception {
        pagar(VALIDO.replace("}", ",\"montoReal\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("montoReal")));
    }

    @Test
    void contenidoNoJson_415() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content(VALIDO))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---------------------------------------------------------------- errores de negocio y del proveedor

    @Test
    void sinSesion_401ConWwwAuthenticate() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(new NoAutenticadoException("sin token"));

        pagar(VALIDO).andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void llaveReutilizada_409() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(new OperacionDuplicadaException("dup"));

        pagar(VALIDO).andExpect(status().isConflict());
    }

    @Test
    void rechazoDelProveedor_422() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(new PagoRechazadoException("rechazo"));

        pagar(VALIDO).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value(422));
    }

    @Test
    void pinBloqueado_423() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(new CuentaBloqueadaException("bloqueado"));

        pagar(VALIDO).andExpect(status().isLocked());
    }

    @Test
    void proveedorNoDisponible_502() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any()))
                .thenThrow(new ProveedorNoDisponibleException("no disponible", HttpStatus.BAD_GATEWAY));

        pagar(VALIDO).andExpect(status().isBadGateway());
    }

    @Test
    void errorInesperado_500SinDetallesInternos() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any()))
                .thenThrow(new IllegalStateException("SELECT * FROM tb_cliente WHERE pin = '1234'"));

        pagar(VALIDO).andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("tb_cliente"))))
                .andExpect(content().string(not(containsString("1234"))));
    }

    // ---------------------------------------------------------------- conciliación

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"upc\":\"abcde12345\",\"aplicada\":true}",
            "{\"upc\":\"ABCDE1234\",\"aplicada\":true}",
            "{\"upc\":\"ABCDE123456\",\"aplicada\":true}",
            "{\"upc\":\"ABCDE12345\"}",
            "{\"upc\":\"ABCDE12345\",\"aplicada\":\"true\"}",
            "{\"upc\":\"ABCDE12345\",\"aplicada\":true,\"monto\":1}"
    })
    void conciliacionConDatosInvalidos_400(String cuerpo) throws Exception {
        mockMvc.perform(post("/api/v1/pagos/conciliacion").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token").content(cuerpo))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(conciliacionPagoService);
    }
}
