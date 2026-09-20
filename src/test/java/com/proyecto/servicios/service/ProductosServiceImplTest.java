package com.proyecto.servicios.service;

import com.proyecto.servicios.client.ProductosClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.mapper.ProductoMapper;
import com.proyecto.servicios.model.productos.MensajeXml;
import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.model.productos.ProductoXml;
import com.proyecto.servicios.model.productos.ProductosXml;
import com.proyecto.servicios.model.productos.ProductosXmlResponse;
import com.proyecto.servicios.service.Impl.ProductosServiceImpl;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductosServiceImplTest {

    @Mock
    private ProductosClient productosClient;

    @Mock
    private GestoPagoTokenService tokenService;

    @Mock
    private ProductoMapper productoMapper;

    private ProductosServiceImpl productosService;

    private final Integer idDistribuidor = 83;
    private final String codigoDispositivo = "GPS83-TPV-17";

    @BeforeEach
    void setUp() {
        productosService = new ProductosServiceImpl(
                productosClient,
                tokenService,
                productoMapper,
                idDistribuidor,
                codigoDispositivo
        );
    }

    private GestoPagoToken crearToken(String tokenValue, String tokenType) {
        GestoPagoToken token = new GestoPagoToken();
        token.setId(1);
        token.setIdDistribuidor(idDistribuidor);
        token.setCodigoDispositivo(codigoDispositivo);
        token.setToken(tokenValue);
        token.setTokenType(tokenType);
        token.setActivo(true);
        return token;
    }

    private ProductosXmlResponse crearRespuestaXmlValida() {
        MensajeXml mensaje = new MensajeXml();
        mensaje.setCodigo("01");
        mensaje.setTexto("Operacion realizada con exito");

        ProductoXml producto = new ProductoXml();
        producto.setServicio("Recarga");
        producto.setProducto("Telcel $100");
        producto.setIdServicio(1);
        producto.setIdProducto(100);
        producto.setIdCatTipoServicio(2);
        producto.setTipoFront(1);
        producto.setHasDigitoVerificador(false);
        producto.setPrecio(new BigDecimal("100.0"));
        producto.setShowAyuda(false);
        producto.setTipoReferencia("NUMERO");
        producto.setLegend("Ingresa tu número");

        ProductosXml productosXml = new ProductosXml();
        productosXml.setProducto(List.of(producto));

        ProductosXmlResponse response = new ProductosXmlResponse();
        response.setMensaje(mensaje);
        response.setProductos(productosXml);

        return response;
    }

    private void mockProductoMapper() {
        lenient().when(productoMapper.toResponse(any(ProductoXml.class))).thenAnswer(invocation -> {
            ProductoXml xml = invocation.getArgument(0);
            if (xml == null) return null;
            ProductoResponse response = new ProductoResponse();
            response.setServicio(xml.getServicio());
            response.setProducto(xml.getProducto());
            response.setIdServicio(xml.getIdServicio());
            response.setIdProducto(xml.getIdProducto());
            response.setIdCatTipoServicio(xml.getIdCatTipoServicio());
            response.setTipoFront(xml.getTipoFront());
            response.setHasDigitoVerificador(xml.getHasDigitoVerificador());
            response.setPrecio(xml.getPrecio());
            response.setShowAyuda(xml.getShowAyuda());
            response.setTipoReferencia(xml.getTipoReferencia());
            response.setLegend(xml.getLegend());
            return response;
        });
    }

    @Test
    @DisplayName("1. Consulta exitosa usando token existente")
    void testConsultaExitosaConTokenExistente() {
        mockProductoMapper();
        GestoPagoToken token = crearToken("token-prueba", "Bearer");
        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.of(token));

        ProductosXmlResponse responseXml = crearRespuestaXmlValida();
        when(productosClient.obtenerProductos("Bearer token-prueba"))
                .thenReturn(responseXml);

        List<ProductoResponse> resultado = productosService.obtenerProductos();

        assertNotNull(resultado);
        assertFalse(resultado.isEmpty());
        assertEquals(1, resultado.size());

        ProductoResponse prod = resultado.get(0);
        assertEquals("Recarga", prod.getServicio());
        assertEquals("Telcel $100", prod.getProducto());
        assertEquals(100, prod.getIdProducto());
        assertEquals(1, prod.getIdServicio());
        assertEquals(2, prod.getIdCatTipoServicio());
        assertEquals(1, prod.getTipoFront());
        assertFalse(prod.getHasDigitoVerificador());
        assertEquals(new BigDecimal("100.0"), prod.getPrecio());
        assertFalse(prod.getShowAyuda());
        assertEquals("NUMERO", prod.getTipoReferencia());
        assertEquals("Ingresa tu número", prod.getLegend());

        verify(productosClient).obtenerProductos("Bearer token-prueba");
        verify(tokenService, times(1)).obtenerTokenActivo(idDistribuidor, codigoDispositivo);
        verify(tokenService, never()).renovarToken();
        verify(productoMapper).toResponse(any(ProductoXml.class));
    }

    @Test
    @DisplayName("2. Renovación cuando no existe token inicialmente")
    void testRenovacionCuandoNoExisteToken() {
        mockProductoMapper();
        GestoPagoToken token = crearToken("token-renovado", "Bearer");

        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(token));

        ProductosXmlResponse responseXml = crearRespuestaXmlValida();
        when(productosClient.obtenerProductos("Bearer token-renovado"))
                .thenReturn(responseXml);

        List<ProductoResponse> resultado = productosService.obtenerProductos();

        assertNotNull(resultado);
        assertFalse(resultado.isEmpty());
        assertEquals(1, resultado.size());

        verify(tokenService).renovarToken();
        verify(tokenService, times(2)).obtenerTokenActivo(idDistribuidor, codigoDispositivo);
        verify(productosClient).obtenerProductos("Bearer token-renovado");
        verify(productoMapper).toResponse(any(ProductoXml.class));
    }

    @Test
    @DisplayName("3. Error de autenticación 401")
    void testErrorAutenticacion401() {
        GestoPagoToken token = crearToken("token-prueba", "Bearer");
        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.of(token));

        Request request = Request.create(Request.HttpMethod.GET, "/test", Collections.emptyMap(), null, new RequestTemplate());
        FeignException feignException = new FeignException.Unauthorized("Unauthorized", request, null, null);

        when(productosClient.obtenerProductos("Bearer token-prueba"))
                .thenThrow(feignException);

        IntegrationException ex = assertThrows(
                IntegrationException.class,
                () -> productosService.obtenerProductos()
        );

        assertEquals(ErrorCode.AUTHENTICATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("4. Error de autenticación 403")
    void testErrorAutenticacion403() {
        GestoPagoToken token = crearToken("token-prueba", "Bearer");
        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.of(token));

        Request request = Request.create(Request.HttpMethod.GET, "/test", Collections.emptyMap(), null, new RequestTemplate());
        FeignException feignException = new FeignException.Forbidden("Forbidden", request, null, null);

        when(productosClient.obtenerProductos("Bearer token-prueba"))
                .thenThrow(feignException);

        IntegrationException ex = assertThrows(
                IntegrationException.class,
                () -> productosService.obtenerProductos()
        );

        assertEquals(ErrorCode.AUTHENTICATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("5. Error HTTP 500")
    void testErrorHttp500() {
        GestoPagoToken token = crearToken("token-prueba", "Bearer");
        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.of(token));

        Request request = Request.create(Request.HttpMethod.GET, "/test", Collections.emptyMap(), null, new RequestTemplate());
        FeignException feignException = FeignException.errorStatus(
                "obtenerProductos",
                Response.builder().status(500).request(request).build()
        );

        when(productosClient.obtenerProductos("Bearer token-prueba"))
                .thenThrow(feignException);

        IntegrationException ex = assertThrows(
                IntegrationException.class,
                () -> productosService.obtenerProductos()
        );

        assertEquals(ErrorCode.HTTP_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("6. Timeout con GatewayTimeout o SocketTimeoutException")
    void testTimeout() {
        GestoPagoToken token = crearToken("token-prueba", "Bearer");
        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.of(token));

        Request request = Request.create(Request.HttpMethod.GET, "/test", Collections.emptyMap(), null, new RequestTemplate());
        FeignException.GatewayTimeout gatewayTimeout = new FeignException.GatewayTimeout("Gateway Timeout", request, null, null);

        when(productosClient.obtenerProductos("Bearer token-prueba"))
                .thenThrow(gatewayTimeout);

        IntegrationException ex1 = assertThrows(
                IntegrationException.class,
                () -> productosService.obtenerProductos()
        );
        assertEquals(ErrorCode.TIMEOUT_ERROR, ex1.getErrorCode());

        doThrow(new RuntimeException(new SocketTimeoutException("Read timed out")))
                .when(productosClient).obtenerProductos("Bearer token-prueba");

        IntegrationException ex2 = assertThrows(
                IntegrationException.class,
                () -> productosService.obtenerProductos()
        );
        assertEquals(ErrorCode.TIMEOUT_ERROR, ex2.getErrorCode());
    }

    @Test
    @DisplayName("7. Respuesta null")
    void testRespuestaNull() {
        GestoPagoToken token = crearToken("token-prueba", "Bearer");
        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.of(token));

        when(productosClient.obtenerProductos("Bearer token-prueba"))
                .thenReturn(null);

        IntegrationException ex = assertThrows(
                IntegrationException.class,
                () -> productosService.obtenerProductos()
        );

        assertEquals(ErrorCode.INVALID_RESPONSE, ex.getErrorCode());
    }

    @Test
    @DisplayName("8. Respuesta sin mensaje")
    void testRespuestaSinMensaje() {
        GestoPagoToken token = crearToken("token-prueba", "Bearer");
        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.of(token));

        ProductosXmlResponse responseSinMensaje = new ProductosXmlResponse();
        responseSinMensaje.setMensaje(null);

        when(productosClient.obtenerProductos("Bearer token-prueba"))
                .thenReturn(responseSinMensaje);

        IntegrationException ex = assertThrows(
                IntegrationException.class,
                () -> productosService.obtenerProductos()
        );

        assertEquals(ErrorCode.INVALID_RESPONSE, ex.getErrorCode());
    }

    @Test
    @DisplayName("9. Token vacío o no disponible")
    void testTokenVacioONoDisponible() {
        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.empty());

        IntegrationException ex = assertThrows(
                IntegrationException.class,
                () -> productosService.obtenerProductos()
        );

        assertEquals(ErrorCode.AUTHENTICATION_ERROR, ex.getErrorCode());
        verify(tokenService).renovarToken();
        verifyNoInteractions(productosClient);

        // Caso token con valor vacío
        GestoPagoToken tokenVacio = crearToken("", "Bearer");
        when(tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo))
                .thenReturn(Optional.of(tokenVacio));

        IntegrationException ex2 = assertThrows(
                IntegrationException.class,
                () -> productosService.obtenerProductos()
        );
        assertEquals(ErrorCode.AUTHENTICATION_ERROR, ex2.getErrorCode());
    }

    @Test
    @DisplayName("10. Validación DTO ProductoResponse equals, hashCode, toString")
    void testDtoEqualsAndHashCode() {
        ProductoResponse p1 = new ProductoResponse();
        p1.setServicio("Recarga");
        p1.setProducto("Telcel $100");
        p1.setIdServicio(1);
        p1.setIdProducto(100);
        p1.setPrecio(new BigDecimal("100.0"));

        ProductoResponse p2 = new ProductoResponse();
        p2.setServicio("Recarga");
        p2.setProducto("Telcel $100");
        p2.setIdServicio(1);
        p2.setIdProducto(100);
        p2.setPrecio(new BigDecimal("100.0"));

        ProductoResponse p3 = new ProductoResponse();
        p3.setServicio("Paquete");
        p3.setProducto("Movistar $50");

        assertAll("Validaciones DTO ProductoResponse",
                () -> assertTrue(p1.equals(p2)),
                () -> assertTrue(p2.equals(p1)),
                () -> assertFalse(p1.equals(null)),
                () -> assertFalse(p1.equals(new Object())),
                () -> assertEquals(p1, p2),
                () -> assertNotEquals(p1, p3),
                () -> assertEquals(p1.hashCode(), p2.hashCode()),
                () -> assertNotNull(p1.toString()),
                () -> assertTrue(p1.toString().contains("Telcel $100"))
        );
    }
}
