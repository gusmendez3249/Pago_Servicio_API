package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.client.SendTxClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.mapper.PagoServicioMapper;
import com.proyecto.servicios.model.gestopago.DatosProveedor;
import com.proyecto.servicios.model.gestopago.SendTxPayload;
import com.proyecto.servicios.service.CifradoService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.Impl.ConsultaEstadoPagoServiceImpl;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * confirmTx de punta a punta (conciliación real + consulta real, con Feign, token y cifrado simulados): 06 confirma con
 * folio, 70 rechaza con un solo reintegro, cualquier otra cosa deja la operación INDETERMINADA, solo se consulta entre
 * 62 s y 24 h, nunca una PENDIENTE, y siempre con exactamente el payload guardado.
 */
@ExtendWith(MockitoExtension.class)
class ConfirmTxConciliacionTest {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final String UPC = "K3J9X0A7ZQ";
    private static final BigDecimal MONTO = new BigDecimal("100.00");
    private static final String PAYLOAD = SendTxPayload.porTelefono(2, 1, "4181234567", MONTO,
            "20260101120000", UPC, "UNIDAD1").aJson();

    @Mock private SendTxClient client;
    @Mock private GestoPagoTokenService tokenService;
    @Mock private CifradoService cifradoService;
    @Mock private TransaccionPagoService transaccionPagoService;
    @Mock private SesionService sesionService;

    private ConciliacionPagoService conciliacion;

    @BeforeEach
    void setUp() {
        ConsultaEstadoPagoServiceImpl consulta = new ConsultaEstadoPagoServiceImpl(client, tokenService, cifradoService,
                83, "DISPOSITIVO", "", 5000, 15000);
        conciliacion = new ConciliacionPagoService(transaccionPagoService, consulta, sesionService,
                new PagoServicioMapper(cifradoService, new com.proyecto.servicios.service.CatalogoPresentacionService("http://img", new com.proyecto.servicios.config.CatalogoProperties())), 5, ZONA.getId());
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("token-vigente");
        lenient().when(tokenService.obtenerTokenActivo(83, "DISPOSITIVO")).thenReturn(Optional.of(token));
        lenient().when(cifradoService.cifrar(anyString())).thenReturn("cifrado");
        lenient().when(transaccionPagoService.reclamarConsultaConfirmacion(eq(20L), any(), any())).thenReturn(true);
    }

    private TransaccionPagoEntity tx(EstatusTransaccionPago estatus, Duration antiguedad, String payload) {
        CuentaEntity cuenta = CuentaEntity.builder().id(5L).numeroCuenta("695429041188").build();
        return TransaccionPagoEntity.builder().id(20L).upc(UPC).monto(MONTO).estatus(estatus).cuenta(cuenta)
                .fechaTransaccion(LocalDateTime.now(ZONA).minus(antiguedad)).payloadSendtx(payload).build();
    }

    private TransaccionPagoEntity indeterminada() {
        return tx(EstatusTransaccionPago.INDETERMINADA, Duration.ofMinutes(3), PAYLOAD);
    }

    private void respondeConfirmTx(String xml) {
        when(client.confirmarTransaccion(eq("Bearer token-vigente"), isNull(), any(), any(Request.Options.class)))
                .thenReturn(xml.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- (4) 06 confirma, 70 rechaza

    @Test
    void confirmTx06_ConfirmaConFolioEIdTx() {
        respondeConfirmTx("<RESPONSE><CODIGO>06</CODIGO><TEXTO>Aplicada</TEXTO><NUM_AUTORIZACION>FOL-77</NUM_AUTORIZACION>"
                + "<ID_TX>TX-88</ID_TX><COMISION>1.50</COMISION><SALDO>999999.00</SALDO></RESPONSE>");

        conciliacion.resolverConProveedor(indeterminada());

        ArgumentCaptor<DatosProveedor> datos = ArgumentCaptor.forClass(DatosProveedor.class);
        verify(transaccionPagoService).confirmar(eq(20L), eq("06"), eq("Aplicada"), datos.capture());
        assertEquals("FOL-77", datos.getValue().numAutorizacion());
        assertEquals("TX-88", datos.getValue().idTx());
        assertEquals(new BigDecimal("1.50"), datos.getValue().comision());
        assertFalse(datos.getValue().toString().contains("999999"), "el SALDO del distribuidor nunca se lee");
        verify(transaccionPagoService, never()).rechazarYReintegrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmTx70_RechazaConUnSoloReintegro() {
        respondeConfirmTx("<RESPONSE><CODIGO>70</CODIGO><TEXTO>No aplicada</TEXTO></RESPONSE>");

        conciliacion.resolverConProveedor(indeterminada());

        verify(transaccionPagoService, times(1)).rechazarYReintegrar(20L, 5L, MONTO, "70", "No aplicada",
                MotivoPago.RECHAZADO_PROVEEDOR);
        verify(transaccionPagoService, never()).confirmar(any(), any(), any(), any());
    }

    @Test
    void confirmTx70_UnaSegundaPasadaNoReintegraDeNuevo() {
        // el reclamo atómico (30 s entre consultas) y el UPDATE condicionado impiden un segundo reintegro
        respondeConfirmTx("<RESPONSE><CODIGO>70</CODIGO><TEXTO>No aplicada</TEXTO></RESPONSE>");
        when(transaccionPagoService.reclamarConsultaConfirmacion(eq(20L), any(), any())).thenReturn(true, false);
        TransaccionPagoEntity tx = indeterminada();

        conciliacion.resolverConProveedor(tx);
        conciliacion.resolverConProveedor(tx);

        verify(client, times(1)).confirmarTransaccion(any(), any(), any(), any(Request.Options.class));
        verify(transaccionPagoService, times(1)).rechazarYReintegrar(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"99", "01", "82", ""})
    void confirmTxConOtroCodigo_SigueIndeterminadaConElMontoRetenido(String codigo) {
        respondeConfirmTx("<RESPONSE><CODIGO>" + codigo + "</CODIGO><TEXTO>otro</TEXTO></RESPONSE>");

        conciliacion.resolverConProveedor(indeterminada());

        verify(transaccionPagoService, never()).confirmar(any(), any(), any(), any());
        verify(transaccionPagoService, never()).rechazarYReintegrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmTxConTimeoutOXmlIlegible_SigueIndeterminada() {
        when(client.confirmarTransaccion(any(), any(), any(), any(Request.Options.class)))
                .thenThrow(new RuntimeException(new SocketTimeoutException("Read timed out")))
                .thenReturn("no es xml".getBytes(StandardCharsets.UTF_8));

        conciliacion.resolverConProveedor(indeterminada());
        conciliacion.resolverConProveedor(indeterminada());

        verify(transaccionPagoService, never()).confirmar(any(), any(), any(), any());
        verify(transaccionPagoService, never()).rechazarYReintegrar(any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- (5) ventana de consulta

    @Test
    void antesDe62Segundos_NoSeLlamaAFeign() {
        conciliacion.resolverConProveedor(tx(EstatusTransaccionPago.INDETERMINADA, Duration.ofSeconds(60), PAYLOAD));

        verifyNoInteractions(client);
        verify(transaccionPagoService, never()).reclamarConsultaConfirmacion(any(), any(), any());
    }

    @Test
    void despuesDe24Horas_NoSeLlamaAFeign() {
        conciliacion.resolverConProveedor(
                tx(EstatusTransaccionPago.INDETERMINADA, Duration.ofHours(24).plusMinutes(1), PAYLOAD));

        verifyNoInteractions(client);
    }

    @Test
    void unaPendienteNuncaSeConsulta() {
        conciliacion.resolverConProveedor(tx(EstatusTransaccionPago.PENDIENTE, Duration.ofMinutes(10), PAYLOAD));

        verifyNoInteractions(client);
        verify(transaccionPagoService, never()).reclamarConsultaConfirmacion(any(), any(), any());
    }

    @Test
    void filaAntiguaSinPayload_NoSeConsulta_QuedaParaConciliacionManual() {
        conciliacion.resolverConProveedor(tx(EstatusTransaccionPago.INDETERMINADA, Duration.ofMinutes(10), null));

        verifyNoInteractions(client);
    }

    @Test
    void consultaRecienteReclamadaPorOtroProceso_NoSeVuelveALlamar() {
        when(transaccionPagoService.reclamarConsultaConfirmacion(eq(20L), any(), any())).thenReturn(false);

        conciliacion.resolverConProveedor(indeterminada());

        verifyNoInteractions(client);
    }

    @Test
    void elReclamoExigeAlMenos30SegundosDesdeLaUltimaConsulta() {
        respondeConfirmTx("<RESPONSE><CODIGO>99</CODIGO></RESPONSE>");
        ArgumentCaptor<LocalDateTime> ahora = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);

        conciliacion.resolverConProveedor(indeterminada());

        verify(transaccionPagoService).reclamarConsultaConfirmacion(eq(20L), ahora.capture(), limite.capture());
        assertEquals(30, Duration.between(limite.getValue(), ahora.getValue()).getSeconds());
    }

    // ---------------------------------------------------------------- (6) mismos parámetros que el sendTx original

    @Test
    void confirmTxReenviaExactamenteElPayloadGuardado() {
        respondeConfirmTx("<RESPONSE><CODIGO>99</CODIGO></RESPONSE>");

        conciliacion.resolverConProveedor(indeterminada());

        verify(cifradoService).cifrar(PAYLOAD);
        ArgumentCaptor<MultiValueMap<String, String>> formulario = ArgumentCaptor.forClass(MultiValueMap.class);
        ArgumentCaptor<Request.Options> opciones = ArgumentCaptor.forClass(Request.Options.class);
        verify(client).confirmarTransaccion(eq("Bearer token-vigente"), isNull(), formulario.capture(), opciones.capture());
        assertEquals("cifrado", formulario.getValue().getFirst("signed"));
        assertNotNull(formulario.getValue().getFirst("random"));
        assertEquals(15000, opciones.getValue().readTimeoutMillis(), "confirmTx lee con 15 s, no con los 65 s de sendTx");
    }

    @Test
    void elPayloadSobreviveElViajeJsonSinCambios() {
        SendTxPayload original = SendTxPayload.porReferencia(2, 1, "REF123", "1111111111", new BigDecimal("523.47"),
                "20260101120000", UPC, null);

        assertEquals(original, SendTxPayload.deJson(original.aJson()));
        assertEquals(original.aJson(), SendTxPayload.deJson(original.aJson()).aJson());
    }

    // ---------------------------------------------------------------- planificador

    @Test
    void elPlanificadorResuelveCadaIndeterminadaYUnaFallaNoDetieneLasDemas() {
        TransaccionPagoEntity a = indeterminada();
        TransaccionPagoEntity b = indeterminada();
        b.setId(21L);
        when(transaccionPagoService.buscarAnteriores(eq(EstatusTransaccionPago.PENDIENTE), any())).thenReturn(java.util.List.of());
        when(transaccionPagoService.buscarAnteriores(eq(EstatusTransaccionPago.INDETERMINADA), any()))
                .thenReturn(java.util.List.of(a, b));
        when(transaccionPagoService.reclamarConsultaConfirmacion(eq(20L), any(), any())).thenThrow(new IllegalStateException("bd"));
        when(transaccionPagoService.reclamarConsultaConfirmacion(eq(21L), any(), any())).thenReturn(true);
        respondeConfirmTx("<RESPONSE><CODIGO>06</CODIGO><NUM_AUTORIZACION>F1</NUM_AUTORIZACION></RESPONSE>");

        conciliacion.conciliar();

        verify(transaccionPagoService).confirmar(eq(21L), eq("06"), any(), any());
    }
}
