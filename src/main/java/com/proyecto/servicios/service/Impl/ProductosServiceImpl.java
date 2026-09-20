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

        try {
            validarConfiguracion();

            GestoPagoToken tokenEntity =
                    tokenService.obtenerTokenActivo(
                            idDistribuidor,
                            codigoDispositivo
                    ).orElseGet(() -> {
                        tokenService.renovarToken();

                        return tokenService.obtenerTokenActivo(
                                idDistribuidor,
                                codigoDispositivo
                        ).orElseThrow(() ->
                                new IntegrationException(
                                        ErrorCode.AUTHENTICATION_ERROR
                                )
                        );
                    });

            validarToken(tokenEntity);

            String tipoToken = tokenEntity.getTokenType();

            if (tipoToken == null || tipoToken.isBlank()) {
                tipoToken = "Bearer";
            }

            String authorization =
                    tipoToken.trim()
                            + " "
                            + tokenEntity.getToken().trim();

            ProductosXmlResponse response =
                    productosClient.obtenerProductos(authorization);

            validarRespuesta(response);

            List<ProductoResponse> productos =
                    response.getProductos() != null
                            && response.getProductos().getProducto() != null
                            ? response.getProductos()
                                    .getProducto()
                                    .stream()
                                    .map(productoMapper::toResponse)
                                    .toList()
                            : Collections.emptyList();

            log.info(
                    "Fin consulta de productos. Total={}",
                    productos.size()
            );

            return productos;

        } catch (IntegrationException exception) {
            throw exception;

        } catch (FeignException exception) {
            manejarFeignException(exception);
            return Collections.emptyList();

        } catch (Exception exception) {
            if (esTimeout(exception)) {
                log.error("Timeout al consultar productos");
                throw new IntegrationException(
                        ErrorCode.TIMEOUT_ERROR,
                        exception
                );
            }

            log.error("Error de comunicación al consultar productos");
            throw new IntegrationException(
                    ErrorCode.COMMUNICATION_ERROR,
                    exception
            );
        }
    }

    private void validarConfiguracion() {
        if (idDistribuidor == null
                || codigoDispositivo == null
                || codigoDispositivo.isBlank()) {

            log.error(
                    "Configuración de GestoPago incompleta o faltante"
            );

            throw new IntegrationException(
                    ErrorCode.CONFIGURATION_ERROR
            );
        }
    }

    private void validarToken(GestoPagoToken tokenEntity) {
        if (tokenEntity == null
                || tokenEntity.getToken() == null
                || tokenEntity.getToken().isBlank()) {

            log.error("Token no disponible o vacío");

            throw new IntegrationException(
                    ErrorCode.AUTHENTICATION_ERROR
            );
        }
    }

    private void validarRespuesta(ProductosXmlResponse response) {
        if (response == null
                || response.getMensaje() == null) {

            throw new IntegrationException(
                    ErrorCode.INVALID_RESPONSE
            );
        }
    }

    private void manejarFeignException(FeignException exception) {
        int status = exception.status();

        if (esTimeout(exception)
                || status == 408
                || status == 504) {

            log.error(
                    "Timeout al consultar servicio externo. status={}",
                    status
            );

            throw new IntegrationException(
                    ErrorCode.TIMEOUT_ERROR,
                    exception
            );
        }

        if (status == 401
                || status == 403
                || exception instanceof FeignException.Unauthorized
                || exception instanceof FeignException.Forbidden) {

            log.error(
                    "Error de autenticación al consultar productos. status={}",
                    status
            );

            throw new IntegrationException(
                    ErrorCode.AUTHENTICATION_ERROR,
                    exception
            );
        }

        log.error(
                "Respuesta HTTP no exitosa del servicio externo. status={}",
                status
        );

        throw new IntegrationException(
                ErrorCode.HTTP_ERROR,
                exception
        );
    }

    private boolean esTimeout(Throwable exception) {
        Throwable actual = exception;

        while (actual != null) {
            if (actual instanceof SocketTimeoutException
                    || actual instanceof java.util.concurrent.TimeoutException
                    || actual instanceof FeignException.GatewayTimeout) {

                return true;
            }

            actual = actual.getCause();
        }

        return false;
    }
}