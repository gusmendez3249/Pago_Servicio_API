package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.ValidarReferenciaRequest;
import com.proyecto.servicios.dto.onboarding.ValidarReferenciaResponse;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.EstadoValidacionReferencia;
import com.proyecto.servicios.enums.MotivoValidacionReferencia;
import com.proyecto.servicios.enums.SituacionSaldoReferencia;
import com.proyecto.servicios.enums.VerificacionReferencia;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.NoAutenticadoException;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.gestopago.ResultadoVerifyReference;
import com.proyecto.servicios.model.gestopago.VerifyReferencePayload;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import com.proyecto.servicios.service.VerifyReferenceService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Validación de referencia de punta a punta con Redis y Gesto Pago simulados (matriz 1 a 5 del Sprint 2). */
@ExtendWith(MockitoExtension.class)
class ValidacionReferenciaServiceTest {

    private static final String AUTH = "Bearer token";

    @Mock private SesionService sesionService;
    @Mock private ProductoPostgresRepository productoRepository;
    @Mock private VerifyReferenceService verifyReferenceService;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valores;

    private ValidacionReferenciaService service;
    private final ValidarReferenciaRequest request = ValidarReferenciaRequest.builder().idSer(1).idProd(2).referencia("REF123").build();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valores);
        ComprobanteReferenciaService comprobantes = new ComprobanteReferenciaService(redis, true, 300, "2", "4");
        service = new ValidacionReferenciaService(sesionService, productoRepository, verifyReferenceService, comprobantes);
        lenient().when(sesionService.autenticar(AUTH))
                .thenReturn(new SesionUsuario(7L, "cliente1", SesionUsuario.ROL_CLIENTE, 10L));
    }

    /** Producto elegido (idSer 1, idProd 2). */
    private void elegido(int tipoFront) {
        ProductoPostgresEntity p = new ProductoPostgresEntity();
        p.setTipoFront(tipoFront);
        p.setIdServicio(1);
        p.setIdProducto(2);
        p.setServicio("CFE");
        p.setProducto("Recibo");
        when(productoRepository.findFirstByIdServicioAndIdProducto(1, 2)).thenReturn(Optional.of(p));
    }

    /** Producto de consulta hermano (mismo idServicio) con idProducto 398. */
    private void conHermanoDeConsulta() {
        ProductoPostgresEntity c = new ProductoPostgresEntity();
        c.setTipoFront(4);
        c.setIdServicio(1);
        c.setIdProducto(398);
        when(productoRepository.findFirstByIdServicioAndTipoFrontInOrderByIdProductoAsc(eq(1), any()))
                .thenReturn(Optional.of(c));
    }

    private void sinHermanoDeConsulta() {
        when(productoRepository.findFirstByIdServicioAndTipoFrontInOrderByIdProductoAsc(eq(1), any()))
                .thenReturn(Optional.empty());
    }

    // ---------------------------------------------------------------- caso A: pago con consulta hermana

    @Test
    void casoA_Adeudo_ConsultaConElProductoHermanoYEmiteComprobanteConMonto() {
        elegido(2);
        conHermanoDeConsulta();
        when(verifyReferenceService.verificar(any())).thenReturn(ResultadoVerifyReference.valida(new BigDecimal("350.00"), "ok"));

        ValidarReferenciaResponse r = service.validar(AUTH, request);

        assertEquals(EstadoValidacionReferencia.VALIDA, r.getEstado());
        assertEquals(VerificacionReferencia.EN_LINEA, r.getVerificacion());
        assertEquals(SituacionSaldoReferencia.ADEUDO, r.getSituacion());
        assertEquals(new BigDecimal("350.00"), r.getSaldo());
        assertEquals(new BigDecimal("350.00"), r.getMontoAPagar());
        assertTrue(r.isPuedePagar());
        assertEquals(300, r.getVigenciaSegundos());
        assertNotNull(r.getIdValidacion());
        assertEquals(2, r.getIdProd());
        assertEquals("CFE", r.getServicio());
        assertEquals("Recibo", r.getProducto());
        ArgumentCaptor<String> valor = ArgumentCaptor.forClass(String.class);
        verify(valores).set(eq("pago:refval:" + r.getIdValidacion()), valor.capture(), eq(Duration.ofSeconds(300)));
        assertTrue(valor.getValue().contains("\"idProd\":2"));
        assertTrue(valor.getValue().contains("\"montoAPagar\":350.00"));

        ArgumentCaptor<VerifyReferencePayload> payload = ArgumentCaptor.forClass(VerifyReferencePayload.class);
        verify(verifyReferenceService).verificar(payload.capture());
        assertEquals(new VerifyReferencePayload(398, 1, "REF123"), payload.getValue());
    }

    @Test
    void casoA_SaldoCeroYNegativo_SinComprobanteNiPago() {
        elegido(2);
        conHermanoDeConsulta();
        when(verifyReferenceService.verificar(any()))
                .thenReturn(ResultadoVerifyReference.valida(BigDecimal.ZERO, "ok"))
                .thenReturn(ResultadoVerifyReference.valida(new BigDecimal("-50"), "ok"));

        ValidarReferenciaResponse cero = service.validar(AUTH, request);
        ValidarReferenciaResponse aFavor = service.validar(AUTH, request);

        assertEquals(SituacionSaldoReferencia.SIN_ADEUDO, cero.getSituacion());
        assertEquals(SituacionSaldoReferencia.SALDO_A_FAVOR, aFavor.getSituacion());
        assertEquals(new BigDecimal("-50"), aFavor.getSaldo());
        for (ValidarReferenciaResponse r : new ValidarReferenciaResponse[]{cero, aFavor}) {
            assertEquals(EstadoValidacionReferencia.VALIDA, r.getEstado());
            assertFalse(r.isPuedePagar());
            assertNull(r.getIdValidacion());
            assertNull(r.getMontoAPagar());
            assertNull(r.getVigenciaSegundos());
        }
        verifyNoInteractions(valores);
    }

    @Test
    void casoA_Invalida_SinComprobanteNiClaveEnRedis() {
        elegido(2);
        conHermanoDeConsulta();
        when(verifyReferenceService.verificar(any())).thenReturn(ResultadoVerifyReference.invalida("No existe"));

        ValidarReferenciaResponse r = service.validar(AUTH, request);

        assertEquals(EstadoValidacionReferencia.INVALIDA, r.getEstado());
        assertEquals(MotivoValidacionReferencia.REFERENCIA_INVALIDA, r.getMotivo());
        assertEquals(VerificacionReferencia.EN_LINEA, r.getVerificacion());
        assertFalse(r.isPuedePagar());
        assertNull(r.getSituacion());
        assertNull(r.getSaldo());
        assertNull(r.getIdValidacion());
        verifyNoInteractions(valores);
    }

    // ---------------------------------------------------------------- caso B: sin consulta del proveedor

    @Test
    void casoB_SoloFormato_NoLlamaAGestoPagoYEmiteComprobanteSinMonto() {
        elegido(2);
        sinHermanoDeConsulta();

        ValidarReferenciaResponse r = service.validar(AUTH, request);

        assertEquals(EstadoValidacionReferencia.VALIDA, r.getEstado());
        assertEquals(VerificacionReferencia.SOLO_FORMATO, r.getVerificacion());
        assertNull(r.getSituacion());
        assertNull(r.getSaldo());
        assertNull(r.getMontoAPagar());
        assertTrue(r.isPuedePagar());
        assertEquals(300, r.getVigenciaSegundos());
        assertNotNull(r.getIdValidacion());
        ArgumentCaptor<String> valor = ArgumentCaptor.forClass(String.class);
        verify(valores).set(eq("pago:refval:" + r.getIdValidacion()), valor.capture(), eq(Duration.ofSeconds(300)));
        assertTrue(valor.getValue().contains("\"montoAPagar\":null"));
        verifyNoInteractions(verifyReferenceService);
    }

    // ---------------------------------------------------------------- caso C: producto de consulta

    @Test
    void casoC_ConsultaConElMismoProducto_NuncaPuedePagarNiEmiteComprobante() {
        elegido(4);
        when(verifyReferenceService.verificar(any())).thenReturn(ResultadoVerifyReference.valida(new BigDecimal("10"), "ok"));

        ValidarReferenciaResponse r = service.validar(AUTH, request);

        assertEquals(EstadoValidacionReferencia.VALIDA, r.getEstado());
        assertEquals(VerificacionReferencia.EN_LINEA, r.getVerificacion());
        assertEquals(SituacionSaldoReferencia.ADEUDO, r.getSituacion());
        assertFalse(r.isPuedePagar());
        assertNull(r.getIdValidacion());
        verify(verifyReferenceService).verificar(new VerifyReferencePayload(2, 1, "REF123"));
        verifyNoInteractions(valores);
    }

    @Test
    void casoC_Invalida() {
        elegido(4);
        when(verifyReferenceService.verificar(any())).thenReturn(ResultadoVerifyReference.invalida("No existe"));

        assertEquals(EstadoValidacionReferencia.INVALIDA, service.validar(AUTH, request).getEstado());
    }

    // ---------------------------------------------------------------- fallas

    @Test
    void fallasDelProveedor_ConBloqueoActivo_PropaganYNuncaEmitenComprobante() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "permitirSinConsulta", false);
        elegido(2);
        conHermanoDeConsulta();
        for (HttpStatus status : new HttpStatus[]{HttpStatus.GATEWAY_TIMEOUT, HttpStatus.BAD_GATEWAY, HttpStatus.INTERNAL_SERVER_ERROR}) {
            doThrow(new ProveedorNoDisponibleException("falla", status)).when(verifyReferenceService).verificar(any());
            assertEquals(status, assertThrows(ProveedorNoDisponibleException.class,
                    () -> service.validar(AUTH, request)).getStatus());
        }
        verifyNoInteractions(valores);
    }

    @Test
    void consultaNoDisponible_PorDefectoContinuaSoloConFormato() {
        elegido(2);
        conHermanoDeConsulta();
        doThrow(new ProveedorNoDisponibleException("falla", HttpStatus.BAD_GATEWAY))
                .when(verifyReferenceService).verificar(any());

        ValidarReferenciaResponse respuesta = service.validar(AUTH, request);

        assertEquals(EstadoValidacionReferencia.VALIDA, respuesta.getEstado());
        assertEquals(VerificacionReferencia.SOLO_FORMATO, respuesta.getVerificacion());
        assertTrue(respuesta.isPuedePagar());
        assertNotNull(respuesta.getIdValidacion());
        assertNull(respuesta.getMontoAPagar());
        assertNull(respuesta.getSaldo());
    }

    @Test
    void consultaNoDisponible_EnProductoDeConsulta_SiguePropagando() {
        elegido(4);
        doThrow(new ProveedorNoDisponibleException("falla", HttpStatus.BAD_GATEWAY))
                .when(verifyReferenceService).verificar(any());

        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ProveedorNoDisponibleException.class,
                () -> service.validar(AUTH, request)).getStatus());
    }

    @Test
    void redisCaidoAlEmitir_503() {
        elegido(2);
        conHermanoDeConsulta();
        when(verifyReferenceService.verificar(any())).thenReturn(ResultadoVerifyReference.valida(BigDecimal.TEN, "ok"));
        doThrow(new RedisConnectionFailureException("caído")).when(valores).set(anyString(), anyString(), any(Duration.class));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ProveedorNoDisponibleException.class,
                () -> service.validar(AUTH, request)).getStatus());
    }

    @Test
    void tipoFrontFueraDeLaLista_400SinLlamarAlProveedor() {
        elegido(3);

        assertThrows(ReglaNegocioException.class, () -> service.validar(AUTH, request));
        verifyNoInteractions(verifyReferenceService);
    }

    @Test
    void productoInexistente_404() {
        when(productoRepository.findFirstByIdServicioAndIdProducto(1, 2)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.validar(AUTH, request));
        verifyNoInteractions(verifyReferenceService);
    }

    @Test
    void ejecutivo_403() {
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(1L, "ejec", SesionUsuario.ROL_EJECUTIVO, null));

        assertThrows(AccesoDenegadoException.class, () -> service.validar(AUTH, request));
        verifyNoInteractions(productoRepository, verifyReferenceService);
    }

    @Test
    void sinSesion_401() {
        when(sesionService.autenticar(any())).thenThrow(new NoAutenticadoException("sin token"));

        assertThrows(NoAutenticadoException.class, () -> service.validar(null, request));
        verifyNoInteractions(productoRepository, verifyReferenceService);
    }

    // ---------------------------------------------------------------- Sprint 3: adeudo mayor al monto máximo

    @Test
    void adeudoMayorAlMontoMaximo_NoSePuedePagarNiEmiteComprobante() {
        elegido(2);
        conHermanoDeConsulta();
        when(verifyReferenceService.verificar(any())).thenReturn(ResultadoVerifyReference.valida(new BigDecimal("10000.01"), "ok"));

        ValidarReferenciaResponse r = service.validar(AUTH, request);

        assertEquals(EstadoValidacionReferencia.VALIDA, r.getEstado());
        assertEquals(SituacionSaldoReferencia.ADEUDO, r.getSituacion());
        assertFalse(r.isPuedePagar());
        assertNull(r.getIdValidacion());
        assertNull(r.getMontoAPagar());
        verifyNoInteractions(valores);
    }

    @Test
    void adeudoIgualAlMontoMaximo_SiSePuedePagar() {
        elegido(2);
        conHermanoDeConsulta();
        when(verifyReferenceService.verificar(any())).thenReturn(ResultadoVerifyReference.valida(new BigDecimal("10000.00"), "ok"));

        ValidarReferenciaResponse r = service.validar(AUTH, request);

        assertTrue(r.isPuedePagar());
        assertEquals(new BigDecimal("10000.00"), r.getMontoAPagar());
        assertNotNull(r.getIdValidacion());
    }
}
