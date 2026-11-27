package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.SendTxClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.model.gestopago.SendTxPayload;
import com.proyecto.servicios.service.CifradoService;
import com.proyecto.servicios.service.ConsultaEstadoPagoService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import feign.Request;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Integración con confirmTx de GestoPago: CODIGO 06 = la operación se aplicó, 70 = no se aplicó. Cualquier otra
 * cosa (otro código, timeout, error HTTP, XML ilegible) deja la operación INDETERMINADA con el monto retenido.
 * Los parámetros son exactamente los del sendTx original (se reconstruyen del JSON guardado en la reserva).
 */
@Slf4j
@Service
public class ConsultaEstadoPagoServiceImpl implements ConsultaEstadoPagoService {

    static final String CODIGO_APLICADA = "06";
    static final String CODIGO_NO_APLICADA = "70";

    private final SendTxClient sendTxClient;
    private final GestoPagoTokenService tokenService;
    private final CifradoService cifradoService;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;
    private final String apiKey;
    private final Request.Options opciones;

    public ConsultaEstadoPagoServiceImpl(
            SendTxClient sendTxClient,
            GestoPagoTokenService tokenService,
            CifradoService cifradoService,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo,
            @Value("${gestopago.api-key:}") String apiKey,
            @Value("${gestopago.confirmtx.connect-timeout:5000}") long connectTimeout,
            @Value("${gestopago.confirmtx.read-timeout:15000}") long readTimeout) {
        this.sendTxClient = sendTxClient;
        this.tokenService = tokenService;
        this.cifradoService = cifradoService;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        this.opciones = new Request.Options(connectTimeout, TimeUnit.MILLISECONDS, readTimeout, TimeUnit.MILLISECONDS, false);
    }

    @Override
    public Optional<ResultadoSendTx> consultar(TransaccionPagoEntity transaccion) {
        String upc = transaccion.getUpc();
        String guardado = transaccion.getPayloadSendtx();
        if (guardado == null || guardado.isBlank()) {
            log.warn("confirmTx no se consulta: la operación no tiene los parámetros de sendTx guardados. upc={}", upc);
            return Optional.empty();
        }

        MultiValueMap<String, String> formulario;
        String authorization;
        try {
            if (idDistribuidor == null || codigoDispositivo == null || codigoDispositivo.isBlank()) {
                log.error("Configuración de GestoPago incompleta o faltante");
                return Optional.empty();
            }
            // Los mismos parámetros del sendTx original: se deserializa (valida) y se vuelve a serializar sin cambios
            SendTxPayload payload = SendTxPayload.deJson(guardado);
            formulario = new LinkedMultiValueMap<>();
            formulario.add("random", String.valueOf(System.currentTimeMillis()));
            formulario.add("signed", cifradoService.cifrar(payload.aJson()));
            authorization = authorization(obtenerToken());
        } catch (IntegrationException | IllegalArgumentException | IllegalStateException exception) {
            log.error("No se consultó confirmTx: fallo previo al envío. upc={} causa={}", upc,
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }

        byte[] respuesta;
        try {
            respuesta = consultarConRenovacionDeToken(authorization, formulario);
        } catch (FeignException exception) {
            log.warn("confirmTx sin resultado. upc={} status={}", upc, exception.status());
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("confirmTx sin resultado. upc={} causa={}", upc, exception.getClass().getSimpleName());
            return Optional.empty();
        }
        return interpretar(respuesta, upc);
    }

    private byte[] consultarConRenovacionDeToken(String authorization, MultiValueMap<String, String> formulario) {
        try {
            return sendTxClient.confirmarTransaccion(authorization, apiKey, formulario, opciones);
        } catch (FeignException.Unauthorized | FeignException.Forbidden exception) {
            // Es una consulta: reintentarla una vez con token nuevo no mueve dinero
            tokenService.renovarToken();
            return sendTxClient.confirmarTransaccion(authorization(obtenerToken()), apiKey, formulario, opciones);
        }
    }

    private Optional<ResultadoSendTx> interpretar(byte[] xml, String upc) {
        if (xml == null || xml.length == 0) {
            log.warn("confirmTx respondió vacío. upc={}", upc);
            return Optional.empty();
        }
        RespuestaXmlGestoPago respuesta;
        try {
            respuesta = RespuestaXmlGestoPago.leer(xml);
        } catch (IllegalArgumentException exception) {
            log.warn("La respuesta de confirmTx no es un XML válido. upc={}", upc);
            return Optional.empty();
        }
        String codigo = respuesta.codigo();
        String texto = respuesta.textoProveedor();
        if (CODIGO_APLICADA.equals(codigo)) {
            log.info("confirmTx: el proveedor aplicó el pago. upc={}", upc);
            return Optional.of(ResultadoSendTx.aprobada(codigo, texto, respuesta.datos()));
        }
        if (CODIGO_NO_APLICADA.equals(codigo)) {
            log.info("confirmTx: el proveedor NO aplicó el pago. upc={}", upc);
            return Optional.of(ResultadoSendTx.rechazada(codigo, texto));
        }
        log.warn("confirmTx sin resultado definitivo. upc={} codigo={} texto={}", upc, codigo, SendTxServiceImpl.paraLog(texto));
        return Optional.empty();
    }

    private GestoPagoToken obtenerToken() {
        GestoPagoToken token = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .orElseGet(() -> {
                    tokenService.renovarToken();
                    return tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                            .orElseThrow(() -> new IntegrationException(ErrorCode.AUTHENTICATION_ERROR));
                });
        if (token.getToken() == null || token.getToken().isBlank()) {
            throw new IntegrationException(ErrorCode.AUTHENTICATION_ERROR);
        }
        return token;
    }

    private static String authorization(GestoPagoToken token) {
        return "Bearer " + token.getToken().trim();
    }
}
