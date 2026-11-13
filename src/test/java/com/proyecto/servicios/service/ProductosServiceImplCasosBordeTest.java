package com.proyecto.servicios.service;

import com.proyecto.servicios.client.ProductosClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.mapper.ProductoMapper;
import com.proyecto.servicios.model.productos.MensajeXml;
import com.proyecto.servicios.model.productos.ProductosXml;
import com.proyecto.servicios.model.productos.ProductosXmlResponse;
import com.proyecto.servicios.service.Impl.ProductosServiceImpl;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Casos borde de la consulta del catálogo de productos de GestoPago. */
@ExtendWith(MockitoExtension.class)
class ProductosServiceImplCasosBordeTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.GET,
            "https://gestopago.portalventas.net/productos", Map.of(), null, StandardCharsets.UTF_8, null);

    @Mock private ProductosClient productosClient;
    @Mock private GestoPagoTokenService tokenService;
    @Mock private ProductoMapper productoMapper;

    private ProductosServiceImpl servicio(Integer distribuidor, String dispositivo) {
        return new ProductosServiceImpl(productosClient, tokenService, productoMapper, distribuidor, dispositivo);
    }

    private ProductosServiceImpl servicio() {
        return servicio(83, "DISP");
    }

    private void tokenTipo(String tipo) {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("abc");
        token.setTokenType(tipo);
        when(tokenService.obtenerTokenActivo(83, "DISP")).thenReturn(Optional.of(token));
    }

    private static ProductosXmlResponse respuestaSinProductos(ProductosXml productos) {
        ProductosXmlResponse response = new ProductosXmlResponse();
        response.setMensaje(new MensajeXml());
        response.setProductos(productos);
        return response;
    }

    private static FeignException http(int status) {
        Response response = Response.builder().status(status).reason("r").request(REQUEST)
                .headers(Map.of()).body(new byte[0]).build();
        return FeignException.errorStatus("obtenerProductos", response);
    }

    private IntegrationException fallaCon(Throwable error) {
        tokenTipo("Bearer");
        when(productosClient.obtenerProductos(anyString())).thenThrow(error);
        ProductosServiceImpl servicio = servicio();
        return assertThrows(IntegrationException.class, servicio::obtenerProductos);
    }

    @ParameterizedTest(name = "distribuidor={0} dispositivo=[{1}]")
    @CsvSource(value = {"NULL, DISP", "83, NULL", "83, ' '"}, nullValues = "NULL")
    void configuracionIncompletaNoConsultaAlProveedor(Integer distribuidor, String dispositivo) {
        ProductosServiceImpl servicio = servicio(distribuidor, dispositivo);

        IntegrationException ex = assertThrows(IntegrationException.class, servicio::obtenerProductos);

        assertEquals(ErrorCode.CONFIGURATION_ERROR, ex.getErrorCode());
        verifyNoInteractions(tokenService, productosClient);
    }

    @Test
    void tipoDeTokenVacioUsaBearerYRespuestaSinProductosDevuelveListaVacia() {
        tokenTipo("  ");
        when(productosClient.obtenerProductos("Bearer abc")).thenReturn(respuestaSinProductos(null));

        assertTrue(servicio().obtenerProductos().isEmpty());
    }

    @Test
    void nodoDeProductosSinElementosDevuelveListaVacia() {
        tokenTipo(null);
        when(productosClient.obtenerProductos("Bearer abc")).thenReturn(respuestaSinProductos(new ProductosXml()));

        assertTrue(servicio().obtenerProductos().isEmpty());
        verifyNoInteractions(productoMapper);
    }

    @Test
    void http408EsTimeout() {
        assertEquals(ErrorCode.TIMEOUT_ERROR, fallaCon(http(408)).getErrorCode());
    }

    @Test
    void fallaConCausaDeTimeoutEsTimeout() {
        assertEquals(ErrorCode.TIMEOUT_ERROR,
                fallaCon(new IllegalStateException(new SocketTimeoutException("read timed out"))).getErrorCode());
    }

    @Test
    void fallaInesperadaEsErrorDeComunicacion() {
        assertEquals(ErrorCode.COMMUNICATION_ERROR, fallaCon(new IllegalStateException("sin red")).getErrorCode());
    }
}
