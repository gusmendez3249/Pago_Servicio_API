package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.ProductosClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.mapper.ProductoMapper;
import com.proyecto.servicios.model.productos.ProductoResponse;
import com.proyecto.servicios.model.productos.ProductosXmlResponse;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.ProductosService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class ProductosServiceImpl implements ProductosService {

    private final ProductosClient productosClient;
    private final GestoPagoTokenService tokenService;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;
    private final ProductoMapper productoMapper;

    public ProductosServiceImpl(
            ProductosClient productosClient,
            GestoPagoTokenService tokenService,
            ProductoMapper productoMapper,
            @Value("${gestopago.auth.id-distribuidor}")
            Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}")
            String codigoDispositivo) {

        this.productosClient = productosClient;
        this.tokenService = tokenService;
        this.productoMapper = productoMapper;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
    }

    @Override
    public List<ProductoResponse> obtenerProductos() {
        log.info("Inicio consulta de productos");
        validarConfiguracion();
        try {
            ProductosXmlResponse response = productosClient.obtenerProductos(authorization(obtenerToken()));
            List<ProductoResponse> productos = convertir(response);
            log.info("Fin consulta de productos. Total={}", productos.size());
            return productos;
        } catch (IntegrationException exception) {
            throw exception;
        } catch (FeignException exception) {
            throw traducir(exception);
        } catch (Exception exception) {
            log.error(esTimeout(exception) ? "Timeout al consultar productos" : "Error de comunicación al consultar productos");
            throw new IntegrationException(esTimeout(exception) ? ErrorCode.TIMEOUT_ERROR : ErrorCode.COMMUNICATION_ERROR,
                    exception);
        }
    }

    private void validarConfiguracion() {
        if (idDistribuidor == null || codigoDispositivo == null || codigoDispositivo.isBlank()) {
            log.error("Configuración de GestoPago incompleta o faltante");
            throw new IntegrationException(ErrorCode.CONFIGURATION_ERROR);
        }
    }

    /** Reutiliza el token vigente; si no hay, lo renueva una vez. */
    private GestoPagoToken obtenerToken() {
        GestoPagoToken token = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .orElseGet(() -> {
                    tokenService.renovarToken();
                    return tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                            .orElseThrow(() -> new IntegrationException(ErrorCode.AUTHENTICATION_ERROR));
                });
        if (token.getToken() == null || token.getToken().isBlank()) {
            log.error("Token no disponible o vacío");
            throw new IntegrationException(ErrorCode.AUTHENTICATION_ERROR);
        }
        return token;
    }

    private static String authorization(GestoPagoToken token) {
        String tipo = token.getTokenType() == null || token.getTokenType().isBlank() ? "Bearer" : token.getTokenType().trim();
        return tipo + " " + token.getToken().trim();
    }

    private List<ProductoResponse> convertir(ProductosXmlResponse response) {
        if (response == null || response.getMensaje() == null) {
            throw new IntegrationException(ErrorCode.INVALID_RESPONSE);
        }
        if (response.getProductos() == null || response.getProductos().getProducto() == null) {
            return Collections.emptyList();
        }
        return response.getProductos().getProducto().stream().map(productoMapper::toResponse).toList();
    }

    /** Traduce el error HTTP del proveedor al código de error de la API. */
    private static IntegrationException traducir(FeignException exception) {
        int status = exception.status();
        if (esTimeout(exception) || status == 408 || status == 504) {
            log.error("Timeout al consultar servicio externo. status={}", status);
            return new IntegrationException(ErrorCode.TIMEOUT_ERROR, exception);
        }
        if (status == 401 || status == 403) {
            log.error("Error de autenticación al consultar productos. status={}", status);
            return new IntegrationException(ErrorCode.AUTHENTICATION_ERROR, exception);
        }
        log.error("Respuesta HTTP no exitosa del servicio externo. status={}", status);
        return new IntegrationException(ErrorCode.HTTP_ERROR, exception);
    }

    private static boolean esTimeout(Throwable exception) {
        for (Throwable actual = exception; actual != null; actual = actual.getCause()) {
            if (actual instanceof SocketTimeoutException
                    || actual instanceof TimeoutException
                    || actual instanceof FeignException.GatewayTimeout) {
                return true;
            }
        }
        return false;
    }
}
