package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.ConciliacionPagoRequest;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.OperacionDuplicadaException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.service.ConsultaEstadoPagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Conciliación automática (scheduler) y manual (ejecutivo) de pagos con resultado desconocido. */
@ExtendWith(MockitoExtension.class)
class ConciliacionPagoServiceTest {

    private static final String AUTH = "Bearer token";
    private static final String UPC = "K3J9X0A7ZQ";
    private static final BigDecimal MONTO = new BigDecimal("100.00");

    @Mock private TransaccionPagoService transaccionPagoService;
    @Mock private ConsultaEstadoPagoService consultaEstadoPagoService;
    @Mock private SesionService sesionService;

    private ConciliacionPagoService service;
    private final SesionUsuario ejecutivo = new SesionUsuario(1L, "ejecutivo", SesionUsuario.ROL_EJECUTIVO, null);
    private final SesionUsuario cliente = new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L);

    @BeforeEach
    void setUp() {
        service = new ConciliacionPagoService(transaccionPagoService, consultaEstadoPagoService, sesionService, 5);
    }

    private TransaccionPagoEntity transaccion(EstatusTransaccionPago estatus) {
        CuentaEntity cuenta = CuentaEntity.builder().id(5L).numeroCuenta("695429041188").build();
        return TransaccionPagoEntity.builder().id(20L).upc(UPC).monto(MONTO).estatus(estatus).cuenta(cuenta)
                .fechaTransaccion(LocalDateTime.now().minusMinutes(10)).build();
    }

    private void sinPendientes() {
        when(transaccionPagoService.buscarAnteriores(eq(EstatusTransaccionPago.PENDIENTE), any())).thenReturn(List.of());
    }

    private void conIndeterminada(ResultadoSendTx resultado) {
        sinPendientes();
        when(transaccionPagoService.buscarAnteriores(eq(EstatusTransaccionPago.INDETERMINADA), any()))
                .thenReturn(List.of(transaccion(EstatusTransaccionPago.INDETERMINADA)));
        when(consultaEstadoPagoService.consultar(UPC)).thenReturn(Optional.ofNullable(resultado));
    }

    // ---------- conciliar() ----------

    @Test
    void pendienteAbandonadoPasaAIndeterminada() {
        when(transaccionPagoService.buscarAnteriores(eq(EstatusTransaccionPago.PENDIENTE), any()))
                .thenReturn(List.of(transaccion(EstatusTransaccionPago.PENDIENTE)));
        when(transaccionPagoService.marcarIndeterminada(eq(20L), isNull(), anyString())).thenReturn(true);
        when(transaccionPagoService.buscarAnteriores(eq(EstatusTransaccionPago.INDETERMINADA), any())).thenReturn(List.of());

        service.conciliar();

        verify(transaccionPagoService).marcarIndeterminada(eq(20L), isNull(), anyString());
        verifyNoInteractions(consultaEstadoPagoService);
    }

    @Test
    void pendienteYaResueltoPorOtroProcesoNoFalla() {
        when(transaccionPagoService.buscarAnteriores(eq(EstatusTransaccionPago.PENDIENTE), any()))
                .thenReturn(List.of(transaccion(EstatusTransaccionPago.PENDIENTE)));
        when(transaccionPagoService.marcarIndeterminada(anyLong(), any(), anyString())).thenReturn(false);
        when(transaccionPagoService.buscarAnteriores(eq(EstatusTransaccionPago.INDETERMINADA), any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.conciliar());
    }

    @Test
    void indeterminadaAprobadaPorProveedorSeConfirma() {
        conIndeterminada(ResultadoSendTx.aprobada("01", "OK"));

        service.conciliar();

        verify(transaccionPagoService).confirmar(20L, "01", "OK");
        verify(transaccionPagoService, never()).rechazarYReintegrar(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void indeterminadaRechazadaSeReintegra() {
        conIndeterminada(ResultadoSendTx.rechazada("05", "Rechazada"));

        service.conciliar();

        verify(transaccionPagoService).rechazarYReintegrar(20L, 5L, MONTO, "05", "Rechazada");
        verify(transaccionPagoService, never()).confirmar(anyLong(), any(), any());
    }

    @Test
    void indeterminadaSinRespuestaDelProveedorQuedaRetenida() {
        conIndeterminada(null);

        service.conciliar();

        verify(transaccionPagoService, never()).confirmar(anyLong(), any(), any());
        verify(transaccionPagoService, never()).rechazarYReintegrar(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void indeterminadaConResultadoAunAmbiguoNoSeToca() {
        conIndeterminada(ResultadoSendTx.indeterminada(null, null, ErrorCode.INTERNAL_ERROR));

        service.conciliar();

        verify(transaccionPagoService, never()).confirmar(anyLong(), any(), any());
        verify(transaccionPagoService, never()).rechazarYReintegrar(anyLong(), anyLong(), any(), any(), any());
    }

    // ---------- resolverManual() ----------

    private ConciliacionPagoRequest solicitud(boolean aplicada) {
        return ConciliacionPagoRequest.builder().upc(UPC).aplicada(aplicada).build();
    }

    @Test
    void clienteNoPuedeConciliar() {
        when(sesionService.autenticar(AUTH)).thenReturn(cliente);
        ConciliacionPagoRequest request = solicitud(true);

        assertThrows(AccesoDenegadoException.class, () -> service.resolverManual(AUTH, request));
        verifyNoInteractions(transaccionPagoService);
    }

    @Test
    void upcInexistenteDevuelveNoEncontrado() {
        when(sesionService.autenticar(AUTH)).thenReturn(ejecutivo);
        when(transaccionPagoService.buscarPorUpc(UPC)).thenReturn(Optional.empty());
        ConciliacionPagoRequest request = solicitud(true);

        assertThrows(RecursoNoEncontradoException.class, () -> service.resolverManual(AUTH, request));
    }

    @Test
    void operacionQueNoEstaIndeterminadaNoSeConcilia() {
        when(sesionService.autenticar(AUTH)).thenReturn(ejecutivo);
        when(transaccionPagoService.buscarPorUpc(UPC))
                .thenReturn(Optional.of(transaccion(EstatusTransaccionPago.CONFIRMADA)));
        ConciliacionPagoRequest request = solicitud(true);

        OperacionDuplicadaException ex = assertThrows(OperacionDuplicadaException.class,
                () -> service.resolverManual(AUTH, request));
        assertTrue(ex.getMessage().contains("CONFIRMADA"));
    }

    @Test
    void ejecutivoConfirmaPagoAplicado() {
        when(sesionService.autenticar(AUTH)).thenReturn(ejecutivo);
        when(transaccionPagoService.buscarPorUpc(UPC))
                .thenReturn(Optional.of(transaccion(EstatusTransaccionPago.INDETERMINADA)));
        when(transaccionPagoService.confirmar(eq(20L), isNull(), contains("ejecutivo"))).thenReturn(true);
        when(transaccionPagoService.saldo(5L)).thenReturn(new BigDecimal("900.00"));

        PagoServicioResponse response = service.resolverManual(AUTH, solicitud(true));

        assertEquals(EstatusTransaccionPago.CONFIRMADA, response.getEstatus());
        assertEquals(new BigDecimal("900.00"), response.getSaldoRestante());
        assertEquals(UPC, response.getUpc());
        assertFalse(response.getNumCuenta().contains("6954290411"), "el número de cuenta debe ir enmascarado");
    }

    @Test
    void ejecutivoRechazaPagoYSeReintegraElMonto() {
        when(sesionService.autenticar(AUTH)).thenReturn(ejecutivo);
        when(transaccionPagoService.buscarPorUpc(UPC))
                .thenReturn(Optional.of(transaccion(EstatusTransaccionPago.INDETERMINADA)));
        when(transaccionPagoService.rechazarYReintegrar(eq(20L), eq(5L), eq(MONTO), isNull(), anyString()))
                .thenReturn(true);
        when(transaccionPagoService.saldo(5L)).thenReturn(new BigDecimal("1000.00"));

        PagoServicioResponse response = service.resolverManual(AUTH, solicitud(false));

        assertEquals(EstatusTransaccionPago.RECHAZADA, response.getEstatus());
        assertTrue(response.getMensajeRespuesta().contains("reintegr"));
    }

    @Test
    void conciliacionConcurrenteYaResueltaFalla() {
        when(sesionService.autenticar(AUTH)).thenReturn(ejecutivo);
        when(transaccionPagoService.buscarPorUpc(UPC))
                .thenReturn(Optional.of(transaccion(EstatusTransaccionPago.INDETERMINADA)));
        when(transaccionPagoService.confirmar(anyLong(), any(), anyString())).thenReturn(false);
        ConciliacionPagoRequest request = solicitud(true);

        assertThrows(OperacionDuplicadaException.class, () -> service.resolverManual(AUTH, request));
    }
}
