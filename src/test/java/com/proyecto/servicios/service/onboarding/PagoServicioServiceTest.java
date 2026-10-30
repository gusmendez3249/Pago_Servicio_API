package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.exception.OperacionDuplicadaException;
import com.proyecto.servicios.exception.PagoRechazadoException;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.model.gestopago.SendTxPayload;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.service.SendTxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Flujo completo del pago: autorización, idempotencia, reserva de saldo y tratamiento de cada desenlace. */
@ExtendWith(MockitoExtension.class)
class PagoServicioServiceTest {

    private static final String AUTH = "Bearer token";
    private static final String LLAVE = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c";
    private static final BigDecimal MONTO = new BigDecimal("100.50");

    @Mock private SesionService sesionService;
    @Mock private CuentaRepository cuentaRepository;
    @Mock private TransaccionPagoService transaccionPagoService;
    @Mock private PinClienteService pinClienteService;
    @Mock private CatalogoPagoService catalogoPagoService;
    @Mock private SendTxService sendTxService;

    private PagoServicioService service;
    private final SesionUsuario cliente = new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L);
    private CuentaEntity cuenta;
    private PagoServicioRequest request;

    @BeforeEach
    void setUp() {
        service = new PagoServicioService(sesionService, cuentaRepository, transaccionPagoService,
                pinClienteService, catalogoPagoService, sendTxService, "", "America/Mexico_City");
        cuenta = CuentaEntity.builder().id(5L).numeroCuenta("695429041188").estatus("ACTIVA")
                .saldo(new BigDecimal("1000.00"))
                .cliente(ClienteEntity.builder().id(10L).activo(true).build())
                .build();
        request = PagoServicioRequest.builder().numCuenta("695429041188").idSer(1).idProd(2).monto(MONTO)
                .esReferencia(false).telefono("4181234567").twoFA("1234").build();
    }

    private void flujoHastaEnvio(ResultadoSendTx resultado) {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.empty());
        when(cuentaRepository.findConClienteByNumeroCuenta("695429041188")).thenReturn(Optional.of(cuenta));
        when(catalogoPagoService.validar(request)).thenReturn(new ProductoPostgresEntity());
        when(transaccionPagoService.reservar(eq(5L), any())).thenAnswer(inv -> {
            TransaccionPagoEntity tx = inv.getArgument(1);
            tx.setId(99L);
            tx.setEstatus(EstatusTransaccionPago.PENDIENTE);
            return tx;
        });
        if (resultado != null) {
            when(sendTxService.enviar(any())).thenReturn(resultado);
        }
    }

    // ---------------------------------------------------------------- desenlaces

    @Test
    void aprobada_ConfirmaYDevuelveCONFIRMADA() {
        flujoHastaEnvio(ResultadoSendTx.aprobada("01", "Transacción exitosa"));
        when(transaccionPagoService.confirmar(eq(99L), eq("01"), any())).thenReturn(true);
        when(transaccionPagoService.saldo(5L)).thenReturn(new BigDecimal("899.50"));

        PagoServicioResponse r = service.procesarPago(AUTH, LLAVE, request);

        assertEquals(EstatusTransaccionPago.CONFIRMADA, r.getEstatus());
        assertEquals("********1188", r.getNumCuenta());
        assertEquals(new BigDecimal("899.50"), r.getSaldoRestante());
        assertTrue(r.getUpc().matches("^[A-Z0-9]{10}$"));
        verify(pinClienteService).verificar(10L, "1234");
        verify(transaccionPagoService, never()).rechazarYReintegrar(any(), any(), any(), any(), any());

        ArgumentCaptor<SendTxPayload> payload = ArgumentCaptor.forClass(SendTxPayload.class);
        verify(sendTxService).enviar(payload.capture());
        assertEquals(r.getUpc(), payload.getValue().getUpc());
        assertEquals(new BigDecimal("100.50"), payload.getValue().getMontoPago());
        assertEquals("4181234567", payload.getValue().getTelefono());
    }

    @Test
    void reservaAntesDeEnviar_ConLlaveYHuellaNormalizadas() {
        flujoHastaEnvio(ResultadoSendTx.aprobada("01", null));
        when(transaccionPagoService.confirmar(anyLong(), any(), any())).thenReturn(true);

        service.procesarPago(AUTH, LLAVE.toUpperCase(), request);

        ArgumentCaptor<TransaccionPagoEntity> tx = ArgumentCaptor.forClass(TransaccionPagoEntity.class);
        var orden = inOrder(transaccionPagoService, sendTxService);
        orden.verify(transaccionPagoService).reservar(eq(5L), tx.capture());
        orden.verify(sendTxService).enviar(any());
        assertEquals(LLAVE, tx.getValue().getIdempotencyKey());
        assertEquals(PagoServicioService.huella(request), tx.getValue().getHashSolicitud());
        assertEquals(7L, tx.getValue().getUsuarioId());
    }

    @Test
    void rechazada_ReintegraYResponde422() {
        flujoHastaEnvio(ResultadoSendTx.rechazada("05", "Referencia inválida"));

        PagoRechazadoException ex = assertThrows(PagoRechazadoException.class,
                () -> service.procesarPago(AUTH, LLAVE, request));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertFalse(ex.getMessage().contains("Referencia inválida"), "no se expone el texto crudo del proveedor");
        verify(transaccionPagoService).rechazarYReintegrar(99L, 5L, MONTO, "05", "Referencia inválida");
    }

    @Test
    void noEnviada_ReintegraYResponde502() {
        flujoHastaEnvio(ResultadoSendTx.noEnviada(ErrorCode.COMMUNICATION_ERROR));

        ProveedorNoDisponibleException ex = assertThrows(ProveedorNoDisponibleException.class,
                () -> service.procesarPago(AUTH, LLAVE, request));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        verify(transaccionPagoService).rechazarYReintegrar(eq(99L), eq(5L), eq(MONTO), isNull(), any());
    }

    @Test
    void noEnviadaPorConfiguracion_Responde500() {
        flujoHastaEnvio(ResultadoSendTx.noEnviada(ErrorCode.CONFIGURATION_ERROR));

        ProveedorNoDisponibleException ex = assertThrows(ProveedorNoDisponibleException.class,
                () -> service.procesarPago(AUTH, LLAVE, request));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void timeout_QuedaINDETERMINADA_SinReintegrar() {
        flujoHastaEnvio(ResultadoSendTx.indeterminada(null, null, ErrorCode.TIMEOUT_ERROR));

        PagoServicioResponse r = service.procesarPago(AUTH, LLAVE, request);

        assertEquals(EstatusTransaccionPago.INDETERMINADA, r.getEstatus());
        verify(transaccionPagoService).marcarIndeterminada(eq(99L), isNull(), any());
        verify(transaccionPagoService, never()).rechazarYReintegrar(any(), any(), any(), any(), any());
        verify(transaccionPagoService, never()).confirmar(any(), any(), any());
    }

    @Test
    void fallaInesperadaAlEnviar_SeTrataComoINDETERMINADA() {
        flujoHastaEnvio(null);
        when(sendTxService.enviar(any())).thenThrow(new IllegalStateException("boom"));

        PagoServicioResponse r = service.procesarPago(AUTH, LLAVE, request);

        assertEquals(EstatusTransaccionPago.INDETERMINADA, r.getEstatus());
        verify(transaccionPagoService, never()).rechazarYReintegrar(any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- autorización y validaciones previas

    @Test
    void ejecutivo_NoPuedePagar() {
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(1L, "ejec", SesionUsuario.ROL_EJECUTIVO, null));

        assertThrows(AccesoDenegadoException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verifyNoInteractions(transaccionPagoService, sendTxService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "3f1c2a9e7b4d4c1e9a2b5d6e7f8a9b0c", " 3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c",
            "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0cX", "3f1c2a9e-7b4d-0c1e-9a2b-5d6e7f8a9b0c"})
    void idempotencyKeyAusenteOInvalida_Responde400(String llave) {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);

        assertThrows(ReglaNegocioException.class, () -> service.procesarPago(AUTH, llave, request));
        verifyNoInteractions(transaccionPagoService, sendTxService);
    }

    @Test
    void cuentaDeOtroCliente_RespondeComoInexistente() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.empty());
        cuenta.setCliente(ClienteEntity.builder().id(999L).activo(true).build());
        when(cuentaRepository.findConClienteByNumeroCuenta(any())).thenReturn(Optional.of(cuenta));

        assertThrows(ClienteNoEncontradoException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verifyNoInteractions(pinClienteService, sendTxService);
    }

    @Test
    void cuentaInactiva_NoSeProcesa() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.empty());
        cuenta.setEstatus("BLOQUEADA");
        when(cuentaRepository.findConClienteByNumeroCuenta(any())).thenReturn(Optional.of(cuenta));

        assertThrows(ClienteInactivoException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verifyNoInteractions(pinClienteService, sendTxService);
    }

    @Test
    void pinIncorrecto_NoReservaNiEnvia() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.empty());
        when(cuentaRepository.findConClienteByNumeroCuenta(any())).thenReturn(Optional.of(cuenta));
        doThrow(new AccesoDenegadoException("PIN")).when(pinClienteService).verificar(10L, "1234");

        assertThrows(AccesoDenegadoException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verify(transaccionPagoService, never()).reservar(any(), any());
        verifyNoInteractions(sendTxService);
    }

    @Test
    void saldoInsuficiente_NoEnvia() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.empty());
        when(cuentaRepository.findConClienteByNumeroCuenta(any())).thenReturn(Optional.of(cuenta));
        when(transaccionPagoService.reservar(eq(5L), any())).thenThrow(new ReglaNegocioException("Saldo insuficiente"));

        assertThrows(ReglaNegocioException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verifyNoInteractions(sendTxService);
    }

    @Test
    void mismaLlaveEnPeticionesSimultaneas_LaSegundaRecibe409() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.empty());
        when(cuentaRepository.findConClienteByNumeroCuenta(any())).thenReturn(Optional.of(cuenta));
        when(transaccionPagoService.reservar(eq(5L), any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertThrows(OperacionDuplicadaException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verifyNoInteractions(sendTxService);
    }

    // ---------------------------------------------------------------- idempotencia

    private TransaccionPagoEntity previa(EstatusTransaccionPago estatus, Long usuarioId, String huella) {
        return TransaccionPagoEntity.builder().id(99L).cuenta(cuenta).usuarioId(usuarioId).upc("ABCDE12345")
                .monto(MONTO).estatus(estatus).hashSolicitud(huella).codigoRespuesta("01")
                .fechaTransaccion(LocalDateTime.now()).build();
    }

    @Test
    void repeticionDeUnPagoConfirmado_DevuelveElMismoResultadoSinCobrar() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE))
                .thenReturn(Optional.of(previa(EstatusTransaccionPago.CONFIRMADA, 7L, PagoServicioService.huella(request))));

        PagoServicioResponse r = service.procesarPago(AUTH, LLAVE, request);

        assertEquals(EstatusTransaccionPago.CONFIRMADA, r.getEstatus());
        assertEquals("ABCDE12345", r.getUpc());
        verify(transaccionPagoService, never()).reservar(any(), any());
        verifyNoInteractions(pinClienteService, sendTxService);
    }

    @Test
    void repeticionDeUnPagoIndeterminado_SigueEnVerificacion() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE))
                .thenReturn(Optional.of(previa(EstatusTransaccionPago.INDETERMINADA, 7L, PagoServicioService.huella(request))));

        assertEquals(EstatusTransaccionPago.INDETERMINADA, service.procesarPago(AUTH, LLAVE, request).getEstatus());
        verifyNoInteractions(sendTxService);
    }

    @Test
    void repeticionDeUnPagoRechazado_Responde422() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE))
                .thenReturn(Optional.of(previa(EstatusTransaccionPago.RECHAZADA, 7L, PagoServicioService.huella(request))));

        assertThrows(PagoRechazadoException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verifyNoInteractions(sendTxService);
    }

    @Test
    void mismaLlaveConOtroMonto_Responde409() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE))
                .thenReturn(Optional.of(previa(EstatusTransaccionPago.CONFIRMADA, 7L, PagoServicioService.huella(request))));
        request.setMonto(new BigDecimal("999.00"));

        assertThrows(OperacionDuplicadaException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verifyNoInteractions(sendTxService);
    }

    @Test
    void llaveDeOtroUsuario_Responde409() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        when(transaccionPagoService.buscarPorLlave(LLAVE))
                .thenReturn(Optional.of(previa(EstatusTransaccionPago.CONFIRMADA, 8L, PagoServicioService.huella(request))));

        assertThrows(OperacionDuplicadaException.class, () -> service.procesarPago(AUTH, LLAVE, request));
    }

    // ---------------------------------------------------------------- upc

    @Test
    void upc_SiempreTiene10CaracteresMayusculasYDigitos() {
        for (int i = 0; i < 5_000; i++) {
            assertTrue(PagoServicioService.generarUpc().matches("^[A-Z0-9]{10}$"));
        }
    }
}
