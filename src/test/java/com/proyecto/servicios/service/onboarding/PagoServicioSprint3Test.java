package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.enums.OrigenMonto;
import com.proyecto.servicios.exception.PagoRechazadoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.mapper.PagoServicioMapper;
import com.proyecto.servicios.model.gestopago.DatosProveedor;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.model.gestopago.SendTxPayload;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.service.CifradoService;
import com.proyecto.servicios.service.SendTxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sprint 3: folio y datos del proveedor, motivo del rechazo (422 con data), saldo antes del comprobante y repetición idempotente. */
@ExtendWith(MockitoExtension.class)
class PagoServicioSprint3Test {

    private static final String AUTH = "Bearer token";
    private static final String LLAVE = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c";
    private static final BigDecimal MONTO = new BigDecimal("100.50");
    private static final String TEXTO_PROVEEDOR = "Transaccion exitosa texto del proveedor";

    @Mock private SesionService sesionService;
    @Mock private CuentaRepository cuentaRepository;
    @Mock private TransaccionPagoService transaccionPagoService;
    @Mock private PinClienteService pinClienteService;
    @Mock private CatalogoPagoService catalogoPagoService;
    @Mock private ComprobanteReferenciaService comprobanteReferenciaService;
    @Mock private SendTxService sendTxService;
    @Mock private CifradoService cifradoService;

    private PagoServicioService service;
    private final SesionUsuario cliente = new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L);
    private CuentaEntity cuenta;
    private PagoServicioRequest request;

    @BeforeEach
    void setUp() {
        service = new PagoServicioService(sesionService, cuentaRepository, transaccionPagoService, pinClienteService,
                catalogoPagoService, comprobanteReferenciaService, sendTxService,
                new PagoServicioMapper(cifradoService, new com.proyecto.servicios.service.CatalogoPresentacionService("http://img", new com.proyecto.servicios.config.CatalogoProperties())), new MotivoRechazoProveedor("05", "02"), "", "America/Mexico_City");
        cuenta = CuentaEntity.builder().id(5L).numeroCuenta("695429041188").estatus("ACTIVA")
                .saldo(new BigDecimal("1000.00")).cliente(ClienteEntity.builder().id(10L).activo(true).build()).build();
        request = PagoServicioRequest.builder().numCuenta("695429041188").idSer(1).idProd(2).monto(MONTO)
                .esReferencia(false).telefono("4181234567").twoFA("1234").build();
    }

    private ProductoPostgresEntity producto() {
        ProductoPostgresEntity p = new ProductoPostgresEntity();
        p.setServicio("Telcel");
        p.setProducto("Recarga 100");
        p.setTipoFront(1);
        return p;
    }

    private void flujoHastaEnvio(ResultadoSendTx resultado) {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.empty());
        when(cuentaRepository.findConClienteByNumeroCuenta("695429041188")).thenReturn(Optional.of(cuenta));
        when(catalogoPagoService.validar(request)).thenReturn(producto());
        when(comprobanteReferenciaService.consumir(any(), any(), any())).thenReturn(OrigenMonto.PRECIO_FIJO);
        when(transaccionPagoService.reservar(eq(5L), any())).thenAnswer(inv -> {
            TransaccionPagoEntity tx = inv.getArgument(1);
            tx.setId(99L);
            tx.setEstatus(EstatusTransaccionPago.PENDIENTE);
            return tx;
        });
        when(sendTxService.enviar(any())).thenReturn(resultado);
        lenient().when(transaccionPagoService.saldo(5L)).thenReturn(new BigDecimal("899.50"));
    }

    private TransaccionPagoEntity guardada(EstatusTransaccionPago estatus) {
        return TransaccionPagoEntity.builder().id(99L).cuenta(cuenta).usuarioId(7L).upc("ABCDE12345").monto(MONTO)
                .estatus(estatus).hashSolicitud(PagoServicioService.huella(request)).telefono("4181234567")
                .nombreServicio("Telcel").nombreProducto("Recarga 100").fechaTransaccion(LocalDateTime.now()).build();
    }

    // ---------------------------------------------------------------- (1) folio e id_tx, mensaje fijo

    @Test
    void sendTx01ConFolioEIdTx_Responde200ConFolioYMensajeFijo() {
        DatosProveedor datos = new DatosProveedor("TX-778899", "FOL-0098765", new BigDecimal("2.50"), MONTO,
                "20260101120500", null, "GIFT-1234-ABCD", "Gracias por tu pago");
        flujoHastaEnvio(ResultadoSendTx.aprobada("01", TEXTO_PROVEEDOR, datos));
        when(transaccionPagoService.confirmar(eq(99L), eq("01"), eq(TEXTO_PROVEEDOR), any())).thenReturn(true);
        TransaccionPagoEntity confirmada = guardada(EstatusTransaccionPago.CONFIRMADA);
        confirmada.setFolio("FOL-0098765");
        confirmada.setIdTx("TX-778899");
        confirmada.setComision(new BigDecimal("2.50"));
        confirmada.setCodigoRespuesta("01");
        confirmada.setLeyenda("Gracias por tu pago");
        confirmada.setPinServicio("CIFRADO-BASE64");
        when(transaccionPagoService.buscarPorUpc(anyString())).thenReturn(Optional.of(confirmada));
        when(cifradoService.descifrar("CIFRADO-BASE64")).thenReturn("GIFT-1234-ABCD");

        PagoServicioResponse r = service.procesarPago(AUTH, LLAVE, request);

        // el folio y el id_tx se guardan al confirmar
        ArgumentCaptor<DatosProveedor> guardados = ArgumentCaptor.forClass(DatosProveedor.class);
        verify(transaccionPagoService).confirmar(eq(99L), eq("01"), eq(TEXTO_PROVEEDOR), guardados.capture());
        assertEquals("FOL-0098765", guardados.getValue().numAutorizacion());
        assertEquals("TX-778899", guardados.getValue().idTx());

        assertEquals(EstatusTransaccionPago.CONFIRMADA, r.getEstatus());
        assertEquals("FOL-0098765", r.getFolio());
        assertEquals("TX-778899", r.getIdTx());
        assertEquals("FOL-0098765", r.getIdentificadorOperacion());
        assertEquals(new BigDecimal("2.50"), r.getComision());
        assertEquals("GIFT-1234-ABCD", r.getPinServicio());
        assertEquals("Gracias por tu pago", r.getLeyenda());
        assertEquals("Telcel", r.getServicio());
        assertEquals("Recarga 100", r.getProducto());
        assertEquals("******4567", r.getTelefonoEnmascarado());
        assertNull(r.getReferenciaEnmascarada());
        assertNull(r.getMotivo());
        assertEquals(PagoServicioMapper.MENSAJE_CONFIRMADA, r.getMensajeRespuesta());
        assertNotEquals(TEXTO_PROVEEDOR, r.getMensajeRespuesta(), "el texto del proveedor nunca llega al cliente");
    }

    @Test
    void identificadorDeOperacion_FolioLuegoIdTxLuegoUpc() {
        TransaccionPagoEntity tx = guardada(EstatusTransaccionPago.CONFIRMADA);
        PagoServicioMapper mapper = new PagoServicioMapper(cifradoService, new com.proyecto.servicios.service.CatalogoPresentacionService("http://img", new com.proyecto.servicios.config.CatalogoProperties()));
        assertEquals("ABCDE12345", mapper.aRespuesta(tx, "695429041188", null).getIdentificadorOperacion());
        tx.setIdTx("TX-1");
        assertEquals("TX-1", mapper.aRespuesta(tx, "695429041188", null).getIdentificadorOperacion());
        tx.setFolio("F-1");
        assertEquals("F-1", mapper.aRespuesta(tx, "695429041188", null).getIdentificadorOperacion());
    }

    @Test
    void elPinDeTarjetaSoloSaleConEstatusConfirmada() {
        TransaccionPagoEntity tx = guardada(EstatusTransaccionPago.INDETERMINADA);
        tx.setPinServicio("CIFRADO");
        tx.setLeyenda("leyenda");

        PagoServicioResponse r = new PagoServicioMapper(cifradoService, new com.proyecto.servicios.service.CatalogoPresentacionService("http://img", new com.proyecto.servicios.config.CatalogoProperties())).aRespuesta(tx, "695429041188", null);

        assertNull(r.getPinServicio());
        assertNull(r.getLeyenda());
        verifyNoInteractions(cifradoService);
    }

    // ---------------------------------------------------------------- (2) rechazo con motivo

    @ParameterizedTest(name = "CODIGO {0} -> {1}")
    @CsvSource({"05, REFERENCIA_INVALIDA", "02, SERVICIO_NO_DISPONIBLE", "99, RECHAZADO_PROVEEDOR"})
    void rechazoDelProveedor_422ConElResponseCompletoYReintegro(String codigo, MotivoPago motivo) {
        flujoHastaEnvio(ResultadoSendTx.rechazada(codigo, "Texto crudo del proveedor"));

        PagoRechazadoException ex = assertThrows(PagoRechazadoException.class,
                () -> service.procesarPago(AUTH, LLAVE, request));

        verify(transaccionPagoService).rechazarYReintegrar(99L, 5L, MONTO, codigo, "Texto crudo del proveedor", motivo);
        PagoServicioResponse data = (PagoServicioResponse) ex.getDetalle();
        assertEquals(EstatusTransaccionPago.RECHAZADA, data.getEstatus());
        assertEquals(motivo, data.getMotivo());
        assertTrue(data.getUpc().matches("^[A-Z0-9]{10}$"));
        assertEquals("********1188", data.getNumCuenta());
        assertFalse(data.getMensajeRespuesta().contains("Texto crudo"));
        assertFalse(ex.getMessage().contains("Texto crudo"));
        verify(transaccionPagoService, never()).confirmar(any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- (3) CODIGO 06 en sendTx: saldo retenido

    @Test
    void sendTx06_Responde202IndeterminadaConElSaldoRetenido() {
        flujoHastaEnvio(ResultadoSendTx.indeterminada("06", "en proceso", com.proyecto.servicios.enums.ErrorCode.TIMEOUT_ERROR));

        PagoServicioResponse r = service.procesarPago(AUTH, LLAVE, request);

        assertEquals(EstatusTransaccionPago.INDETERMINADA, r.getEstatus());
        assertNull(r.getFolio());
        assertEquals(r.getUpc(), r.getIdentificadorOperacion(), "sin folio se identifica por el upc");
        assertNull(r.getMotivo());
        assertEquals(PagoServicioMapper.MENSAJE_INDETERMINADA, r.getMensajeRespuesta());
        verify(transaccionPagoService).marcarIndeterminada(eq(99L), eq("06"), any());
        verify(transaccionPagoService, never()).rechazarYReintegrar(any(), any(), any(), any(), any(), any());
        verify(transaccionPagoService, never()).rechazarYReintegrar(any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- la reserva guarda la instantánea y el payload exacto

    @Test
    void laReservaGuardaCatalogoOrigenYElPayloadExactoEnviado() {
        flujoHastaEnvio(ResultadoSendTx.aprobada("01", null));
        when(transaccionPagoService.confirmar(anyLong(), any(), any(), any())).thenReturn(true);

        service.procesarPago(AUTH, LLAVE, request);

        ArgumentCaptor<TransaccionPagoEntity> reserva = ArgumentCaptor.forClass(TransaccionPagoEntity.class);
        verify(transaccionPagoService).reservar(eq(5L), reserva.capture());
        ArgumentCaptor<SendTxPayload> enviado = ArgumentCaptor.forClass(SendTxPayload.class);
        verify(sendTxService).enviar(enviado.capture());
        assertEquals(enviado.getValue().aJson(), reserva.getValue().getPayloadSendtx());
        assertEquals("Telcel", reserva.getValue().getNombreServicio());
        assertEquals("Recarga 100", reserva.getValue().getNombreProducto());
        assertEquals(1, reserva.getValue().getTipoFront());
        assertEquals(OrigenMonto.PRECIO_FIJO, reserva.getValue().getOrigenMonto());
    }

    // ---------------------------------------------------------------- (9) saldo insuficiente antes de consumir el comprobante

    @Test
    void saldoInsuficiente_400SinConsumirElComprobante() {
        cuenta.setSaldo(new BigDecimal("50.00"));
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.empty());
        when(cuentaRepository.findConClienteByNumeroCuenta("695429041188")).thenReturn(Optional.of(cuenta));
        when(catalogoPagoService.validar(request)).thenReturn(producto());

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> service.procesarPago(AUTH, LLAVE, request));

        assertEquals(400, ex.getCodigo());
        assertEquals(MotivoPago.SALDO_INSUFICIENTE_CUENTA, ((DetalleErrorPago) ex.getDetalle()).getMotivo());
        verifyNoInteractions(comprobanteReferenciaService, sendTxService);
        verify(transaccionPagoService, never()).reservar(any(), any());
    }

    // ---------------------------------------------------------------- (12) repetición de una RECHAZADA

    @Test
    void repeticionDeUnaRechazada_422ConEstatusRechazadaEnData() {
        TransaccionPagoEntity previa = guardada(EstatusTransaccionPago.RECHAZADA);
        previa.setMotivo(MotivoPago.REFERENCIA_INVALIDA);
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.of(previa));

        PagoRechazadoException ex = assertThrows(PagoRechazadoException.class, () -> service.procesarPago(AUTH, LLAVE, request));

        assertEquals(422, ex.getCodigo());
        PagoServicioResponse data = (PagoServicioResponse) ex.getDetalle();
        assertEquals(EstatusTransaccionPago.RECHAZADA, data.getEstatus());
        assertEquals(MotivoPago.REFERENCIA_INVALIDA, data.getMotivo());
        assertEquals("ABCDE12345", data.getUpc());
        verifyNoInteractions(sendTxService, pinClienteService, comprobanteReferenciaService);
    }

    @Test
    void repeticionDeUnaRechazadaAntigua_SinMotivoGuardado_SeTrataComoRechazoDelProveedor() {
        TransaccionPagoEntity previa = guardada(EstatusTransaccionPago.RECHAZADA);
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.of(previa));

        PagoRechazadoException ex = assertThrows(PagoRechazadoException.class, () -> service.procesarPago(AUTH, LLAVE, request));

        assertEquals(MotivoPago.RECHAZADO_PROVEEDOR, ((PagoServicioResponse) ex.getDetalle()).getMotivo());
    }
}
