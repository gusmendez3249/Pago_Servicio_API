package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.ValidacionReferenciaRequeridaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Comprobante de validación en Redis: emisión con TTL, consumo de un solo uso y ligado a usuario/producto/referencia. */
@ExtendWith(MockitoExtension.class)
class ComprobanteReferenciaServiceTest {

    private static final String ID = "3f1c2a9e-7b4d-4c1e-9a2b-5d6e7f8a9b0c";
    private static final String CLAVE = "pago:refval:" + ID;
    private static final String VALOR = "{\"usuarioId\":7,\"idSer\":1,\"idProd\":2,\"referencia\":\"REF123\",\"montoAPagar\":null}";

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valores;

    private ComprobanteReferenciaService service;
    private final SesionUsuario sesion = new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L);

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valores);
        service = new ComprobanteReferenciaService(redis, true, 300, "2", "4");
    }

    private static PagoServicioRequest pago(boolean referencia, String ref, String idValidacion) {
        return PagoServicioRequest.builder().numCuenta("695429041188").idSer(1).idProd(2)
                .monto(new BigDecimal("100.00")).esReferencia(referencia).reference(referencia ? ref : null)
                .telefono(referencia ? null : "4181234567").idValidacion(idValidacion).twoFA("1234").build();
    }

    private static ProductoPostgresEntity producto(Integer tipoFront) {
        ProductoPostgresEntity p = new ProductoPostgresEntity();
        p.setTipoFront(tipoFront);
        return p;
    }

    // ---------------------------------------------------------------- emitir

    @Test
    void emitir_GuardaConClaveYTtlYDevuelveUuidV4() {
        String id = service.emitir(7L, 1, 2, "REF123", new BigDecimal("350.00"));

        assertEquals(4, UUID.fromString(id).version());
        ArgumentCaptor<String> valor = ArgumentCaptor.forClass(String.class);
        verify(valores).set(eq("pago:refval:" + id), valor.capture(), eq(Duration.ofSeconds(300)));
        assertTrue(valor.getValue().contains("\"usuarioId\":7"));
        assertTrue(valor.getValue().contains("\"referencia\":\"REF123\""));
        assertTrue(valor.getValue().contains("\"montoAPagar\":350.00"));
    }

    @Test
    void emitir_RedisCaido_503() {
        doThrow(new RedisConnectionFailureException("caído")).when(valores).set(anyString(), anyString(), any(Duration.class));

        ProveedorNoDisponibleException ex = assertThrows(ProveedorNoDisponibleException.class,
                () -> service.emitir(7L, 1, 2, "REF123", BigDecimal.TEN));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    // ---------------------------------------------------------------- consumir

    @Test
    void consumir_ComprobanteCorrecto_PasaYSeConsumeConGetAndDelete() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR);

        assertDoesNotThrow(() -> service.consumir(sesion, pago(true, "REF123", ID), producto(2)));
        verify(valores).getAndDelete(CLAVE);
    }

    @Test
    void consumir_IdEnMayusculas_SeNormaliza() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR);

        assertDoesNotThrow(() -> service.consumir(sesion, pago(true, "REF123", ID.toUpperCase()), producto(2)));
    }

    @Test
    void consumir_OtraReferencia_428() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR);

        ValidacionReferenciaRequeridaException ex = assertThrows(ValidacionReferenciaRequeridaException.class,
                () -> service.consumir(sesion, pago(true, "REF999", ID), producto(2)));
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, ex.getStatus());
    }

    @Test
    void consumir_ReferenciaConOtraMayuscula_428() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR);

        assertThrows(ValidacionReferenciaRequeridaException.class,
                () -> service.consumir(sesion, pago(true, "ref123", ID), producto(2)));
    }

    @Test
    void consumir_OtroProducto_428() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR);
        PagoServicioRequest otro = pago(true, "REF123", ID);
        otro.setIdProd(3);

        assertThrows(ValidacionReferenciaRequeridaException.class, () -> service.consumir(sesion, otro, producto(2)));
    }

    @Test
    void consumir_OtroServicio_428() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR);
        PagoServicioRequest otro = pago(true, "REF123", ID);
        otro.setIdSer(9);

        assertThrows(ValidacionReferenciaRequeridaException.class, () -> service.consumir(sesion, otro, producto(2)));
    }

    @Test
    void consumir_OtroUsuario_428() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR);
        SesionUsuario otroUsuario = new SesionUsuario(8L, "cliente2", SesionUsuario.ROL_CLIENTE, 11L);

        assertThrows(ValidacionReferenciaRequeridaException.class,
                () -> service.consumir(otroUsuario, pago(true, "REF123", ID), producto(2)));
    }

    @Test
    void consumir_ExpiradoOYaConsumido_428() {
        when(valores.getAndDelete(CLAVE)).thenReturn(null);

        assertThrows(ValidacionReferenciaRequeridaException.class,
                () -> service.consumir(sesion, pago(true, "REF123", ID), producto(2)));
    }

    @Test
    void consumir_UnSoloUso_ElSegundoIntentoFalla() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR).thenReturn(null);

        service.consumir(sesion, pago(true, "REF123", ID), producto(2));
        assertThrows(ValidacionReferenciaRequeridaException.class,
                () -> service.consumir(sesion, pago(true, "REF123", ID), producto(2)));
    }

    @Test
    void consumir_ValorIlegible_428() {
        when(valores.getAndDelete(CLAVE)).thenReturn("no es json");

        assertThrows(ValidacionReferenciaRequeridaException.class,
                () -> service.consumir(sesion, pago(true, "REF123", ID), producto(2)));
    }

    @Test
    void consumir_SinIdValidacion_428() {
        assertThrows(ValidacionReferenciaRequeridaException.class,
                () -> service.consumir(sesion, pago(true, "REF123", null), producto(2)));
        verifyNoInteractions(valores);
    }

    @Test
    void consumir_RedisCaido_503FallaCerrada() {
        when(valores.getAndDelete(CLAVE)).thenThrow(new RedisConnectionFailureException("caído"));

        ProveedorNoDisponibleException ex = assertThrows(ProveedorNoDisponibleException.class,
                () -> service.consumir(sesion, pago(true, "REF123", ID), producto(2)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    // ---------------------------------------------------------------- respaldo en PostgreSQL (Redis caído)

    @Test
    void conRedisCaido_ElComprobanteSeEmiteYConsumeEnElRespaldo() {
        RespaldoEnMemoria respaldo = new RespaldoEnMemoria();
        ComprobanteReferenciaService conRespaldo = new ComprobanteReferenciaService(redis, respaldo, true, 300, "2", "4");
        doThrow(new RedisConnectionFailureException("caído")).when(valores).set(anyString(), anyString(), any(Duration.class));
        when(valores.getAndDelete(anyString())).thenThrow(new RedisConnectionFailureException("caído"));

        String id = conRespaldo.emitir(7L, 1, 2, "REF123", null);

        assertDoesNotThrow(() -> conRespaldo.consumir(sesion, pago(true, "REF123", id), producto(2)));
        assertThrows(ValidacionReferenciaRequeridaException.class,
                () -> conRespaldo.consumir(sesion, pago(true, "REF123", id), producto(2)), "un solo uso");
    }

    @Test
    void emitidoEnElRespaldo_SeConsumeAunqueRedisYaEsteArriba() {
        RespaldoEnMemoria respaldo = new RespaldoEnMemoria();
        ComprobanteReferenciaService conRespaldo = new ComprobanteReferenciaService(redis, respaldo, true, 300, "2", "4");
        respaldo.guardar(ID, VALOR, Duration.ofSeconds(300));
        when(valores.getAndDelete(CLAVE)).thenReturn(null);

        assertDoesNotThrow(() -> conRespaldo.consumir(sesion, pago(true, "REF123", ID), producto(2)));
    }

    private static final class RespaldoEnMemoria implements RespaldoComprobantes {
        private final java.util.Map<String, String> datos = new java.util.HashMap<>();
        @Override public void guardar(String id, String valor, Duration vigencia) { datos.put(id, valor); }
        @Override public String consumir(String id) { return datos.remove(id); }
    }

    // ---------------------------------------------------------------- hueco del teléfono y exentos

    @Test
    void tipoFront2ConTelefono_400() {
        assertThrows(ReglaNegocioException.class, () -> service.consumir(sesion, pago(false, null, null), producto(2)));
        verifyNoInteractions(valores);
    }

    @Test
    void tipoFront4ConTelefono_NoEsProductoDePago_Exento() {
        assertDoesNotThrow(() -> service.consumir(sesion, pago(false, null, null), producto(4)));
    }

    // ---------------------------------------------------------------- monto a pagar

    private static final String VALOR_CON_MONTO =
            "{\"usuarioId\":7,\"idSer\":1,\"idProd\":2,\"referencia\":\"REF123\",\"montoAPagar\":350.00}";

    @Test
    void montoIgualAlMontoAPagar_PasaElCandado() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR_CON_MONTO);
        PagoServicioRequest p = pago(true, "REF123", ID);
        p.setMonto(new BigDecimal("350"));

        assertDoesNotThrow(() -> service.consumir(sesion, p, producto(2)));
    }

    @Test
    void montoDistintoDelMontoAPagar_400YElComprobanteQuedaConsumido() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR_CON_MONTO);
        PagoServicioRequest p = pago(true, "REF123", ID);
        p.setMonto(new BigDecimal("100.00"));

        assertThrows(ReglaNegocioException.class, () -> service.consumir(sesion, p, producto(2)));
        verify(valores).getAndDelete(CLAVE);
    }

    @Test
    void comprobanteSoloFormato_AceptaElMontoDelRequest() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR);
        PagoServicioRequest p = pago(true, "REF123", ID);
        p.setMonto(new BigDecimal("77.70"));

        assertDoesNotThrow(() -> service.consumir(sesion, p, producto(2)));
    }

    @Test
    void recargaPorTelefono_Exenta() {
        assertDoesNotThrow(() -> service.consumir(sesion, pago(false, null, null), producto(1)));
        assertDoesNotThrow(() -> service.consumir(sesion, pago(false, null, null), new ProductoPostgresEntity()));
        verifyNoInteractions(valores);
    }

    @Test
    void candadoDeshabilitado_NoExigeComprobantePeroSiCierraElHuecoDelTelefono() {
        service = new ComprobanteReferenciaService(redis, false, 300, "2", "4");

        assertDoesNotThrow(() -> service.consumir(sesion, pago(true, "REF123", null), producto(2)));
        assertThrows(ReglaNegocioException.class, () -> service.consumir(sesion, pago(false, null, null), producto(2)));
        verifyNoInteractions(valores);
    }

    @Test
    void requiereVerificacion_SegunLaListaConfigurada() {
        assertTrue(service.esDePago(2));
        assertFalse(service.esDePago(4));
        assertTrue(service.esDeConsulta(4));
        assertFalse(service.esDeConsulta(2));
        assertFalse(service.esDePago(3));
        assertFalse(service.esDePago(null));
        assertEquals(300, service.vigenciaSegundos());
    }

    // ---------------------------------------------------------------- Sprint 3: origen del monto y motivos

    @Test
    void pagoPorTelefono_OrigenPrecioFijo() {
        assertEquals(com.proyecto.servicios.enums.OrigenMonto.PRECIO_FIJO,
                service.consumir(sesion, pago(false, null, null), producto(1)));
        verifyNoInteractions(valores);
    }

    @Test
    void comprobanteConMontoAPagar_OrigenAdeudo() {
        String conMonto = "{\"usuarioId\":7,\"idSer\":1,\"idProd\":2,\"referencia\":\"REF123\",\"montoAPagar\":100.00}";
        when(valores.getAndDelete(CLAVE)).thenReturn(conMonto);

        assertEquals(com.proyecto.servicios.enums.OrigenMonto.ADEUDO, service.consumir(sesion, pago(true, "REF123", ID), producto(2)));
    }

    @Test
    void comprobanteSoloFormato_OrigenCapturado() {
        when(valores.getAndDelete(CLAVE)).thenReturn(VALOR);

        assertEquals(com.proyecto.servicios.enums.OrigenMonto.CAPTURADO, service.consumir(sesion, pago(true, "REF123", ID), producto(2)));
    }

    @Test
    void montoDistintoAlAdeudo_MontoNoPermitido() {
        when(valores.getAndDelete(CLAVE)).thenReturn("{\"usuarioId\":7,\"idSer\":1,\"idProd\":2,\"referencia\":\"REF123\",\"montoAPagar\":250.00}");

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service.consumir(sesion, pago(true, "REF123", ID), producto(2)));

        assertEquals(com.proyecto.servicios.enums.MotivoPago.MONTO_NO_PERMITIDO,
                ((com.proyecto.servicios.dto.onboarding.DetalleErrorPago) ex.getDetalle()).getMotivo());
    }

    @Test
    void sinComprobante_428ConMotivoValidacionRequerida() {
        when(valores.getAndDelete(CLAVE)).thenReturn(null);

        ValidacionReferenciaRequeridaException ex = assertThrows(ValidacionReferenciaRequeridaException.class,
                () -> service.consumir(sesion, pago(true, "REF123", ID), producto(2)));

        assertEquals(com.proyecto.servicios.enums.MotivoPago.VALIDACION_REQUERIDA,
                ((com.proyecto.servicios.dto.onboarding.DetalleErrorPago) ex.getDetalle()).getMotivo());
    }
}
