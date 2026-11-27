package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.dto.onboarding.EstadoPagoRequest;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.mapper.PagoServicioMapper;
import com.proyecto.servicios.service.CifradoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** POST /pagos/estado: solo operaciones propias (404 idéntico si no existe o es ajena), por upc o por Idempotency-Key. */
@ExtendWith(MockitoExtension.class)
class EstadoPagoServiceTest {

    private static final String AUTH = "Bearer token";
    private static final String UPC = "K3J9X0A7ZQ";
    private static final String LLAVE = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c";

    @Mock private SesionService sesionService;
    @Mock private TransaccionPagoService transaccionPagoService;
    @Mock private ConciliacionPagoService conciliacionPagoService;
    @Mock private CifradoService cifradoService;

    private EstadoPagoService service;
    private final SesionUsuario cliente = new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L);

    @BeforeEach
    void setUp() {
        service = new EstadoPagoService(sesionService, transaccionPagoService, conciliacionPagoService,
                new PagoServicioMapper(cifradoService, new com.proyecto.servicios.service.CatalogoPresentacionService("http://img/", new com.proyecto.servicios.config.CatalogoProperties())));
        lenient().when(sesionService.autenticar(AUTH)).thenReturn(cliente);
    }

    private TransaccionPagoEntity tx(Long usuarioId, EstatusTransaccionPago estatus) {
        CuentaEntity cuenta = CuentaEntity.builder().id(5L).numeroCuenta("695429041188").build();
        return TransaccionPagoEntity.builder().id(20L).usuarioId(usuarioId).cuenta(cuenta).upc(UPC)
                .monto(new BigDecimal("100.00")).estatus(estatus).fechaTransaccion(LocalDateTime.now()).build();
    }

    private static EstadoPagoRequest porUpc() {
        return EstadoPagoRequest.builder().upc(UPC).build();
    }

    // ---------------------------------------------------------------- (7)

    @Test
    void upcDeOtroUsuario_404Identico() {
        when(transaccionPagoService.buscarPorUpc(UPC)).thenReturn(Optional.of(tx(99L, EstatusTransaccionPago.CONFIRMADA)));

        RecursoNoEncontradoException ajena = assertThrows(RecursoNoEncontradoException.class,
                () -> service.consultar(AUTH, porUpc()));

        when(transaccionPagoService.buscarPorUpc(UPC)).thenReturn(Optional.empty());
        RecursoNoEncontradoException inexistente = assertThrows(RecursoNoEncontradoException.class,
                () -> service.consultar(AUTH, porUpc()));

        assertEquals(404, ajena.getCodigo());
        assertEquals(MotivoPago.OPERACION_NO_ENCONTRADA, ((DetalleErrorPago) ajena.getDetalle()).getMotivo());
        assertEquals(ajena.getMessage(), inexistente.getMessage());
        assertEquals(((DetalleErrorPago) ajena.getDetalle()).getMotivo(), ((DetalleErrorPago) inexistente.getDetalle()).getMotivo());
        verifyNoInteractions(conciliacionPagoService);
    }

    @Test
    void idempotencyKeyPropia_200ConElEstadoActual() {
        TransaccionPagoEntity confirmada = tx(7L, EstatusTransaccionPago.CONFIRMADA);
        confirmada.setFolio("FOL-1");
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.of(confirmada));
        when(transaccionPagoService.saldo(5L)).thenReturn(new BigDecimal("900.00"));

        PagoServicioResponse r = service.consultar(AUTH,
                EstadoPagoRequest.builder().idempotencyKey(LLAVE.toUpperCase()).build());

        assertEquals(EstatusTransaccionPago.CONFIRMADA, r.getEstatus());
        assertEquals("FOL-1", r.getFolio());
        assertEquals(new BigDecimal("900.00"), r.getSaldoRestante());
        assertEquals("********1188", r.getNumCuenta());
        verify(transaccionPagoService).buscarPorLlave(LLAVE);
    }

    @Test
    void idempotencyKeyDeOtroUsuario_404() {
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.of(tx(99L, EstatusTransaccionPago.CONFIRMADA)));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.consultar(AUTH, EstadoPagoRequest.builder().idempotencyKey(LLAVE).build()));
    }

    @Test
    void indeterminada_ConsultaConfirmTxYResponderConElEstadoReleido() {
        TransaccionPagoEntity indeterminada = tx(7L, EstatusTransaccionPago.INDETERMINADA);
        TransaccionPagoEntity confirmada = tx(7L, EstatusTransaccionPago.CONFIRMADA);
        confirmada.setFolio("FOL-9");
        when(transaccionPagoService.buscarPorUpc(UPC)).thenReturn(Optional.of(indeterminada), Optional.of(confirmada));

        PagoServicioResponse r = service.consultar(AUTH, porUpc());

        verify(conciliacionPagoService).resolverConProveedor(indeterminada);
        assertEquals(EstatusTransaccionPago.CONFIRMADA, r.getEstatus());
        assertEquals("FOL-9", r.getFolio());
    }

    @Test
    void pendiente_NoSeConsultaConElProveedor() {
        when(transaccionPagoService.buscarPorUpc(UPC)).thenReturn(Optional.of(tx(7L, EstatusTransaccionPago.PENDIENTE)));

        PagoServicioResponse r = service.consultar(AUTH, porUpc());

        assertEquals(EstatusTransaccionPago.PENDIENTE, r.getEstatus());
        verifyNoInteractions(conciliacionPagoService);
    }

    @Test
    void rechazada_TraeElMotivoYNuncaElTextoDelProveedor() {
        TransaccionPagoEntity rechazada = tx(7L, EstatusTransaccionPago.RECHAZADA);
        rechazada.setMotivo(MotivoPago.SERVICIO_NO_DISPONIBLE);
        rechazada.setTextoRespuesta("texto crudo del proveedor");
        when(transaccionPagoService.buscarPorUpc(UPC)).thenReturn(Optional.of(rechazada));

        PagoServicioResponse r = service.consultar(AUTH, porUpc());

        assertEquals(MotivoPago.SERVICIO_NO_DISPONIBLE, r.getMotivo());
        assertFalse(r.getMensajeRespuesta().contains("texto crudo"));
    }

    @Test
    void respuestaIncluyeIdServicioIdProductoYLogoUrl() {
        TransaccionPagoEntity t = tx(7L, EstatusTransaccionPago.CONFIRMADA);
        t.setIdServicio(11);
        t.setIdProducto(4);
        when(transaccionPagoService.buscarPorUpc(UPC)).thenReturn(Optional.of(t));

        PagoServicioResponse r = service.consultar(AUTH, porUpc());

        assertEquals(11, r.getIdServicio());
        assertEquals(4, r.getIdProducto());
        assertEquals("http://img/servicios/11.png", r.getLogoUrl());
    }

    @Test
    void ejecutivo_403NoTitular() {
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(1L, "ejec", SesionUsuario.ROL_EJECUTIVO, null));

        AccesoDenegadoException ex = assertThrows(AccesoDenegadoException.class, () -> service.consultar(AUTH, porUpc()));

        assertEquals(MotivoPago.NO_TITULAR, ((DetalleErrorPago) ex.getDetalle()).getMotivo());
        verifyNoInteractions(transaccionPagoService);
    }
}
