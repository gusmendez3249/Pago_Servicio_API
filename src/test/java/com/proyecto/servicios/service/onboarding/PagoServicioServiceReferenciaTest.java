package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.model.gestopago.SendTxPayload;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.mapper.PagoServicioMapper;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pago por referencia (teléfono que exige GestoPago según el producto), generación del upc y cuenta inactiva. */
@ExtendWith(MockitoExtension.class)
class PagoServicioServiceReferenciaTest {

    private static final String AUTH = "Bearer token";
    private static final String LLAVE = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c";

    @Mock private SesionService sesionService;
    @Mock private CuentaRepository cuentaRepository;
    @Mock private TransaccionPagoService transaccionPagoService;
    @Mock private PinClienteService pinClienteService;
    @Mock private CatalogoPagoService catalogoPagoService;
    @Mock private ComprobanteReferenciaService comprobanteReferenciaService;
    @Mock private SendTxService sendTxService;
    @Mock private CifradoService cifradoService;

    private PagoServicioService service;
    private CuentaEntity cuenta;
    private PagoServicioRequest request;

    @BeforeEach
    void setUp() {
        service = new PagoServicioService(sesionService, cuentaRepository, transaccionPagoService,
                pinClienteService, catalogoPagoService, comprobanteReferenciaService, sendTxService,
                new PagoServicioMapper(cifradoService, new com.proyecto.servicios.service.CatalogoPresentacionService("http://img", new com.proyecto.servicios.config.CatalogoProperties())), new MotivoRechazoProveedor("05", "02"), " UNIDAD ", "America/Mexico_City");
        cuenta = CuentaEntity.builder().id(5L).numeroCuenta("695429041188").estatus("ACTIVA")
                .saldo(new BigDecimal("1000.00"))
                .cliente(ClienteEntity.builder().id(10L).activo(true).build()).build();
        request = PagoServicioRequest.builder().numCuenta("695429041188").idSer(1).idProd(2)
                .monto(new BigDecimal("150.00")).esReferencia(true).reference("123456789012").idValidacion("3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c").twoFA("1234").build();
    }

    /** Cliente autenticado, sin pago previo con la misma llave y dueño de la cuenta. */
    private void clienteConSuCuenta() {
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L));
        when(transaccionPagoService.buscarPorLlave(LLAVE)).thenReturn(Optional.empty());
        when(cuentaRepository.findConClienteByNumeroCuenta("695429041188")).thenReturn(Optional.of(cuenta));
    }

    private void hastaElEnvio(ProductoPostgresEntity producto) {
        clienteConSuCuenta();
        when(catalogoPagoService.validar(request)).thenReturn(producto);
        when(transaccionPagoService.reservar(eq(5L), any())).thenAnswer(inv -> {
            TransaccionPagoEntity tx = inv.getArgument(1);
            tx.setId(99L);
            tx.setEstatus(EstatusTransaccionPago.PENDIENTE);
            return tx;
        });
        when(sendTxService.enviar(any())).thenReturn(ResultadoSendTx.aprobada("01", "OK"));
        when(transaccionPagoService.confirmar(anyLong(), any(), any(), any())).thenReturn(true);
    }

    private static ProductoPostgresEntity producto(Integer tipoFront, Integer tipoServicio) {
        ProductoPostgresEntity producto = new ProductoPostgresEntity();
        producto.setTipoFront(tipoFront);
        producto.setIdCatTipoServicio(tipoServicio);
        return producto;
    }

    @ParameterizedTest(name = "tipoFront={0} tipoServicio={1} -> {2}")
    @CsvSource(value = {
            "1, NULL, 1111111111",
            "2, 10, 1111111111",
            "NULL, 10, #1111111111",
            "3, 11, #1111111111",
            "3, 5, NULL",
            "NULL, NULL, NULL"
    }, nullValues = "NULL")
    void pagoPorReferenciaEnviaElTelefonoQueExigeElProducto(Integer tipoFront, Integer tipoServicio, String telefono) {
        hastaElEnvio(producto(tipoFront, tipoServicio));

        service.procesarPago(AUTH, LLAVE, request);

        ArgumentCaptor<SendTxPayload> payload = ArgumentCaptor.forClass(SendTxPayload.class);
        verify(sendTxService).enviar(payload.capture());
        assertEquals(telefono, payload.getValue().telefono());
        ArgumentCaptor<TransaccionPagoEntity> tx = ArgumentCaptor.forClass(TransaccionPagoEntity.class);
        verify(transaccionPagoService).reservar(eq(5L), tx.capture());
        assertEquals("123456789012", tx.getValue().getReferencia());
        assertNull(tx.getValue().getTelefono(), "en pago por referencia no se guarda teléfono del cliente");
    }

    @Test
    void sinUpcDisponibleTrasVariosIntentosNoSeReserva() {
        clienteConSuCuenta();
        when(catalogoPagoService.validar(request)).thenReturn(producto(1, null));
        when(transaccionPagoService.existeUpc(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verify(transaccionPagoService, times(5)).existeUpc(anyString());
        verify(transaccionPagoService, never()).reservar(anyLong(), any());
    }

    @Test
    void cuentaInactivaNoPuedePagar() {
        clienteConSuCuenta();
        cuenta.setEstatus("INACTIVA");

        assertThrows(ClienteInactivoException.class, () -> service.procesarPago(AUTH, LLAVE, request));
        verifyNoInteractions(pinClienteService, sendTxService);
    }

    @Test
    void numerosCortosNoSeEnmascaran() {
        assertNull(PagoServicioService.enmascarar(null));
        assertEquals("1234", PagoServicioService.enmascarar("1234"));
        assertEquals("*2345", PagoServicioService.enmascarar("12345"));
    }
}
