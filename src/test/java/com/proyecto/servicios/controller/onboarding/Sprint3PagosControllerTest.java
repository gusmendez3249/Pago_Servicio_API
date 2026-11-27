package com.proyecto.servicios.controller.onboarding;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.JacksonConfig;
import com.proyecto.servicios.config.onboarding.TextoEstrictoOnboardingConfig;
import com.proyecto.servicios.dto.onboarding.CuentaClienteResponse;
import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.OperacionDuplicadaException;
import com.proyecto.servicios.exception.PagoRechazadoException;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.ValidacionReferenciaRequeridaException;
import com.proyecto.servicios.service.onboarding.ConciliacionPagoService;
import com.proyecto.servicios.service.onboarding.CuentaClienteService;
import com.proyecto.servicios.service.onboarding.EstadoPagoService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contrato HTTP del Sprint 3: /cuentas/mias, /pagos/estado y el campo data.motivo de los errores de pago. */
class Sprint3PagosControllerTest {

    private static final String LLAVE = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c";
    private static final String PAGO = """
            {"numCuenta":"695429041188","idSer":1,"idProd":2,"monto":100.50,"esReferencia":false,
             "telefono":"4181234567","twoFA":"1234"}""";

    private final PagoServicioService pagoServicioService = mock(PagoServicioService.class);
    private final EstadoPagoService estadoPagoService = mock(EstadoPagoService.class);
    private final com.proyecto.servicios.service.onboarding.HistorialPagoService historialPagoService = mock(com.proyecto.servicios.service.onboarding.HistorialPagoService.class);
    private final CuentaClienteService cuentaClienteService = mock(CuentaClienteService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().disableCoercionCustomizer().customize(builder);
        builder.featuresToDisable(MapperFeature.ALLOW_COERCION_OF_SCALARS, DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        builder.modulesToInstall(new TextoEstrictoOnboardingConfig().textoEstrictoOnboardingModule());
        ObjectMapper mapper = builder.build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new PagoServicioController(pagoServicioService, mock(ConciliacionPagoService.class), estadoPagoService, historialPagoService),
                        new CuentaClienteController(cuentaClienteService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private ResultActions json(String url, String cuerpo) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer token").header("Idempotency-Key", LLAVE).content(cuerpo));
    }

    private static PagoServicioResponse respuesta(EstatusTransaccionPago estatus, MotivoPago motivo) {
        return PagoServicioResponse.builder().numCuenta("********1188").upc("ABCDE12345").estatus(estatus).motivo(motivo)
                .monto(new BigDecimal("100.50")).saldoRestante(new BigDecimal("899.50"))
                .identificadorOperacion("ABCDE12345").build();
    }

    // ---------------------------------------------------------------- Sprint 4: /pagos/historial

    @Test
    void historial_CuerpoVacio_200ConLaPagina() throws Exception {
        when(historialPagoService.listar(any(), any())).thenReturn(com.proyecto.servicios.dto.onboarding.HistorialPagosResponse.builder()
                .elementos(List.of(respuesta(EstatusTransaccionPago.CONFIRMADA, null))).pagina(0).tamanio(20).total(57).hayMas(true).build());

        json("/api/v1/pagos/historial", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.data.elementos[0].upc").value("ABCDE12345"))
                .andExpect(jsonPath("$.data.total").value(57))
                .andExpect(jsonPath("$.data.hayMas").value(true));
    }

    @Test
    void historial_ConFiltrosValidos_200() throws Exception {
        when(historialPagoService.listar(any(), any())).thenReturn(com.proyecto.servicios.dto.onboarding.HistorialPagosResponse.builder()
                .elementos(List.of()).pagina(1).tamanio(50).total(0).hayMas(false).build());

        json("/api/v1/pagos/historial", "{\"filtros\":{\"estatus\":\"CONFIRMADA\",\"fechaDesde\":\"2026-11-01\",\"fechaHasta\":\"2026-11-14\"},\"pagina\":1,\"tamanio\":50}")
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"tamanio\":0}", "{\"tamanio\":51}", "{\"pagina\":-1}",
            "{\"filtros\":{\"fechaDesde\":\"2026-11-14\",\"fechaHasta\":\"2026-11-01\"}}",
            "{\"usuarioId\":1}", "{\"filtros\":{\"usuarioId\":1}}"})
    void historial_CuerpoInvalido_400SinLlamarAlServicio(String cuerpo) throws Exception {
        json("/api/v1/pagos/historial", cuerpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(1));
        verifyNoInteractions(historialPagoService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"tamanio\":\"20\"}", "{\"filtros\":{\"fechaDesde\":\"2026-02-31\"}}",
            "{\"filtros\":{\"fechaDesde\":\"14/11/2026\"}}", "{\"filtros\":{\"estatus\":\"OTRO\"}}"})
    void historial_TipoOFormatoInvalido_400SinLlamarAlServicio(String cuerpo) throws Exception {
        json("/api/v1/pagos/historial", cuerpo).andExpect(status().isBadRequest());
        verifyNoInteractions(historialPagoService);
    }

    @Test
    void historial_Ejecutivo_403() throws Exception {
        when(historialPagoService.listar(any(), any())).thenThrow(new AccesoDenegadoException("solo titular", MotivoPago.NO_TITULAR));

        json("/api/v1/pagos/historial", "{}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.motivo").value("NO_TITULAR"));
    }

    @Test
    void historial_SinToken_401() throws Exception {
        when(historialPagoService.listar(any(), any()))
                .thenThrow(new com.proyecto.servicios.exception.NoAutenticadoException("sin sesion"));

        json("/api/v1/pagos/historial", "{}").andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 8. /cuentas/mias

    @Test
    void cuentasMias_200ConLaListaDeCuentasActivas() throws Exception {
        when(cuentaClienteService.listarMias(any())).thenReturn(List.of(CuentaClienteResponse.builder()
                .numCuenta("695429041188").numCuentaEnmascarada("********1188").saldo(new BigDecimal("1000.00")).estatus("ACTIVA").build()));

        json("/api/v1/cuentas/mias", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.data[0].numCuenta").value("695429041188"))
                .andExpect(jsonPath("$.data[0].numCuentaEnmascarada").value("********1188"))
                .andExpect(jsonPath("$.data[0].saldo").value(1000.00))
                .andExpect(jsonPath("$.data[0].estatus").value("ACTIVA"));
    }

    @Test
    void cuentasMias_ListaVacia_200() throws Exception {
        when(cuentaClienteService.listarMias(any())).thenReturn(List.of());

        json("/api/v1/cuentas/mias", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void cuentasMias_Ejecutivo_403ConMotivoNoTitular() throws Exception {
        when(cuentaClienteService.listarMias(any()))
                .thenThrow(new AccesoDenegadoException("solo titular", MotivoPago.NO_TITULAR));

        json("/api/v1/cuentas/mias", "{}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403))
                .andExpect(jsonPath("$.data.motivo").value("NO_TITULAR"));
    }

    @Test
    void cuentasMias_CuerpoConCamposExtra_400() throws Exception {
        json("/api/v1/cuentas/mias", "{\"clienteId\":1}").andExpect(status().isBadRequest());
        verifyNoInteractions(cuentaClienteService);
    }

    // ---------------------------------------------------------------- 7. /pagos/estado

    @Test
    void estadoPorUpc_200() throws Exception {
        when(estadoPagoService.consultar(any(), any())).thenReturn(respuesta(EstatusTransaccionPago.CONFIRMADA, null));

        json("/api/v1/pagos/estado", "{\"upc\":\"ABCDE12345\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estatus").value("CONFIRMADA"))
                .andExpect(jsonPath("$.data.identificadorOperacion").value("ABCDE12345"));
    }

    @Test
    void estadoPorIdempotencyKey_200() throws Exception {
        when(estadoPagoService.consultar(any(), any())).thenReturn(respuesta(EstatusTransaccionPago.INDETERMINADA, null));

        json("/api/v1/pagos/estado", "{\"idempotencyKey\":\"" + LLAVE + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estatus").value("INDETERMINADA"));
    }

    @Test
    void estadoDeUpcAjeno_404ConMotivoOperacionNoEncontrada() throws Exception {
        when(estadoPagoService.consultar(any(), any()))
                .thenThrow(new RecursoNoEncontradoException("no", MotivoPago.OPERACION_NO_ENCONTRADA));

        json("/api/v1/pagos/estado", "{\"upc\":\"ABCDE12345\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404))
                .andExpect(jsonPath("$.data.motivo").value("OPERACION_NO_ENCONTRADA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"upc\":\"ABCDE12345\",\"idempotencyKey\":\"3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c\"}",
            "{\"upc\":\"abcde12345\"}",
            "{\"upc\":\"ABCDE1234\"}",
            "{\"idempotencyKey\":\"no-es-uuid\"}",
            "{\"upc\":\"ABCDE12345\",\"extra\":1}"
    })
    void estadoConCuerpoInvalido_400SinLlegarAlServicio(String cuerpo) throws Exception {
        json("/api/v1/pagos/estado", cuerpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value(1));
        verifyNoInteractions(estadoPagoService);
    }

    // ---------------------------------------------------------------- A2: tabla de respuestas del pago

    @Test
    void rechazo05_422ConElResponseCompletoYElMotivo() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(
                new PagoRechazadoException("rechazo", respuesta(EstatusTransaccionPago.RECHAZADA, MotivoPago.REFERENCIA_INVALIDA)));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(422))
                .andExpect(jsonPath("$.data.estatus").value("RECHAZADA"))
                .andExpect(jsonPath("$.data.motivo").value("REFERENCIA_INVALIDA"))
                .andExpect(jsonPath("$.data.upc").value("ABCDE12345"));
    }

    @Test
    void repeticionDeUnaRechazada_422ConDataEstatusRechazada() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(
                new PagoRechazadoException("repetida", respuesta(EstatusTransaccionPago.RECHAZADA, MotivoPago.RECHAZADO_PROVEEDOR)));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.estatus").value("RECHAZADA"));
    }

    @Test
    void sendTx06_202ConEstatusIndeterminada() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any()))
                .thenReturn(respuesta(EstatusTransaccionPago.INDETERMINADA, null));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.estatus").value("INDETERMINADA"))
                .andExpect(jsonPath("$.data.folio").doesNotExist());
    }

    @Test
    void pinIncorrecto_403ConIntentosRestantes_SinCamposNulos() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(new AccesoDenegadoException("pin",
                DetalleErrorPago.builder().motivo(MotivoPago.PIN_INCORRECTO).intentosRestantes(2).build()));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.motivo").value("PIN_INCORRECTO"))
                .andExpect(jsonPath("$.data.intentosRestantes").value(2))
                .andExpect(jsonPath("$.data.minutosBloqueo").doesNotExist());
    }

    @Test
    void pinBloqueado_423ConMinutosDeBloqueo() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(new CuentaBloqueadaException("bloqueado",
                DetalleErrorPago.builder().motivo(MotivoPago.PIN_BLOQUEADO).minutosBloqueo(15).build()));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().isLocked())
                .andExpect(jsonPath("$.data.motivo").value("PIN_BLOQUEADO"))
                .andExpect(jsonPath("$.data.minutosBloqueo").value(15));
    }

    @Test
    void saldoInsuficiente_400ConMotivo() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any()))
                .thenThrow(new ReglaNegocioException("saldo", MotivoPago.SALDO_INSUFICIENTE_CUENTA));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.data.motivo").value("SALDO_INSUFICIENTE_CUENTA"));
    }

    @Test
    void llaveEnUso_409ConMotivo() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any()))
                .thenThrow(new OperacionDuplicadaException("dup", MotivoPago.LLAVE_EN_USO));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.motivo").value("LLAVE_EN_USO"));
    }

    @Test
    void validacionRequerida_428ConMotivo() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any()))
                .thenThrow(new ValidacionReferenciaRequeridaException("falta", MotivoPago.VALIDACION_REQUERIDA));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.data.motivo").value("VALIDACION_REQUERIDA"));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void proveedorNoDisponible_ConMotivoEnData(int codigo) throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(
                new ProveedorNoDisponibleException("no", HttpStatus.valueOf(codigo), MotivoPago.PROVEEDOR_NO_DISPONIBLE));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().is(codigo))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.data.motivo").value("PROVEEDOR_NO_DISPONIBLE"));
    }

    @Test
    void errorDeNegocioSinMotivo_DataSigueSiendoNull() throws Exception {
        when(pagoServicioService.procesarPago(any(), any(), any())).thenThrow(new ReglaNegocioException("sin motivo"));

        json("/api/v1/pagos/servicio", PAGO).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").doesNotExist());
    }
}
