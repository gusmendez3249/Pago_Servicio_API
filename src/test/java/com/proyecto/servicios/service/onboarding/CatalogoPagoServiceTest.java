package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.PagoServicioRequest;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Servicio, producto y monto deben corresponder al catálogo: evita montos manipulados. */
@ExtendWith(MockitoExtension.class)
class CatalogoPagoServiceTest {

    @Mock private ProductoPostgresRepository productoRepository;

    private CatalogoPagoService service;

    @BeforeEach
    void setUp() {
        service = new CatalogoPagoService(productoRepository, new BigDecimal("1.00"), new BigDecimal("10000.00"), "1,2");
    }

    private PagoServicioRequest request(String monto) {
        return PagoServicioRequest.builder().idSer(1).idProd(2).monto(new BigDecimal(monto)).build();
    }

    private void producto(int tipoFront, String precio) {
        ProductoPostgresEntity p = new ProductoPostgresEntity();
        p.setTipoFront(tipoFront);
        p.setPrecio(precio == null ? null : new BigDecimal(precio));
        when(productoRepository.findFirstByIdServicioAndIdProducto(1, 2)).thenReturn(Optional.of(p));
    }

    @Test
    void productoInexistente_Responde404() {
        when(productoRepository.findFirstByIdServicioAndIdProducto(1, 2)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.validar(request("100.00")));
    }

    @Test
    void recargaDePrecioFijo_ExigeElMontoExacto() {
        producto(1, "100");

        assertDoesNotThrow(() -> service.validar(request("100.00")));
        assertThrows(ReglaNegocioException.class, () -> service.validar(request("99.99")));
        assertThrows(ReglaNegocioException.class, () -> service.validar(request("100.01")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.00", "10.00", "523.47", "10000.00"})
    void pagoDeServicio_MontoAbiertoAunqueElPrecioSeaLaComision(String monto) {
        producto(2, "10.0");

        assertDoesNotThrow(() -> service.validar(request(monto)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.99", "10000.01", "50000.00"})
    void pagoDeServicioFueraDeLimites_SeRechaza(String monto) {
        producto(2, "0");

        assertThrows(ReglaNegocioException.class, () -> service.validar(request(monto)));
    }

    @Test
    void consultaDeSaldo_NoAdmitePagos() {
        producto(4, "0");

        assertThrows(ReglaNegocioException.class, () -> service.validar(request("100.00")));
    }

    // ---------------------------------------------------------------- Sprint 3: total de contado fijado por el servidor

    private static com.proyecto.servicios.enums.MotivoPago motivo(ReglaNegocioException ex) {
        return ((com.proyecto.servicios.dto.onboarding.DetalleErrorPago) ex.getDetalle()).getMotivo();
    }

    @Test
    void tipoFront1ConMontoDistintoAlPrecio_400MontoNoPermitido() {
        producto(1, "100");

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> service.validar(request("99.99")));

        assertEquals(400, ex.getCodigo());
        assertEquals(com.proyecto.servicios.enums.MotivoPago.MONTO_NO_PERMITIDO, motivo(ex));
    }

    @Test
    void tipoFront5_400ProductoNoPagable() {
        producto(5, "100");

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> service.validar(request("100.00")));

        assertEquals(com.proyecto.servicios.enums.MotivoPago.PRODUCTO_NO_PAGABLE, motivo(ex));
    }

    @Test
    void consultaDeSaldoTipoFront4_ProductoNoPagable() {
        producto(4, "0");

        assertEquals(com.proyecto.servicios.enums.MotivoPago.PRODUCTO_NO_PAGABLE,
                motivo(assertThrows(ReglaNegocioException.class, () -> service.validar(request("100.00")))));
    }

    @Test
    void tipoFront1SinPrecioValido_ProductoNoPagable() {
        producto(1, null);

        assertEquals(com.proyecto.servicios.enums.MotivoPago.PRODUCTO_NO_PAGABLE,
                motivo(assertThrows(ReglaNegocioException.class, () -> service.validar(request("100.00")))));
    }

    @Test
    void tipoFront1PagadoConReferencia_ProductoNoPagable() {
        producto(1, "100");
        PagoServicioRequest conReferencia = request("100.00");
        conReferencia.setEsReferencia(true);

        assertEquals(com.proyecto.servicios.enums.MotivoPago.PRODUCTO_NO_PAGABLE,
                motivo(assertThrows(ReglaNegocioException.class, () -> service.validar(conReferencia))));
    }

    @Test
    void pagoDeServicioFueraDeLimites_MontoNoPermitido() {
        producto(2, "0");

        assertEquals(com.proyecto.servicios.enums.MotivoPago.MONTO_NO_PERMITIDO,
                motivo(assertThrows(ReglaNegocioException.class, () -> service.validar(request("10000.01")))));
    }

    @Test
    void lasListaDeTiposPagablesEsConfigurable() {
        CatalogoPagoService soloServicios = new CatalogoPagoService(productoRepository, new BigDecimal("1.00"),
                new BigDecimal("10000.00"), "2");
        producto(1, "100");

        assertEquals(com.proyecto.servicios.enums.MotivoPago.PRODUCTO_NO_PAGABLE,
                motivo(assertThrows(ReglaNegocioException.class, () -> soloServicios.validar(request("100.00")))));
    }
}
