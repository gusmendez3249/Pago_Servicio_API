package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.SendTxClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.model.gestopago.SendTxPayload;
import com.proyecto.servicios.service.CifradoService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.SendTxService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.net.ssl.SSLHandshakeException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Integración con sendTx de GestoPago. Regla central: un fallo solo se considera "no procesado" cuando hay certeza
 * de que la petición no llegó al proveedor o fue rechazada antes de procesarse. Todo lo demás (timeout de lectura,
 * 5xx, respuesta ilegible, códigos ambiguos) es INDETERMINADA: no se reintenta y se resuelve por conciliación.
 */
@Slf4j
@Service
public class SendTxServiceImpl implements SendTxService {

    private static final String CODIGO_EXITO = "01";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SendTxClient sendTxClient;
    private final GestoPagoTokenService tokenService;
    private final CifradoService cifradoService;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;
    private final String apiKey;
    private final Set<String> codigosIndeterminados;

    public SendTxServiceImpl(
            SendTxClient sendTxClient,
            GestoPagoTokenService tokenService,
            CifradoService cifradoService,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo,
            @Value("${gestopago.api-key:}") String apiKey,
            @Value("${gestopago.sendtx.codigos-indeterminados:82}") String codigosIndeterminados) {

        this.sendTxClient = sendTxClient;
        this.tokenService = tokenService;
        this.cifradoService = cifradoService;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        this.codigosIndeterminados = Arrays.stream(codigosIndeterminados.split(","))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public ResultadoSendTx enviar(SendTxPayload payload) {
        // --- Antes de enviar: cualquier error aquí garantiza que GestoPago no recibió nada ---
        MultiValueMap<String, String> formulario;
        String authorization;
        try {
            if (idDistribuidor == null || codigoDispositivo == null || codigoDispositivo.isBlank()) {
                log.error("Configuración de GestoPago incompleta o faltante");
                return ResultadoSendTx.noEnviada(ErrorCode.CONFIGURATION_ERROR);
            }
            formulario = new LinkedMultiValueMap<>();
            formulario.add("random", String.valueOf(System.currentTimeMillis()));
            formulario.add("signed", cifradoService.cifrar(serializar(payload)));
            authorization = authorization(obtenerToken());
        } catch (IntegrationException exception) {
            log.error("No se envió sendTx: fallo previo al envío. upc={} causa={}", payload.getUpc(), exception.getErrorCode());
            return ResultadoSendTx.noEnviada(exception.getErrorCode());
        }

        // --- Envío ---
        byte[] respuesta;
        try {
            respuesta = enviarConRenovacionDeToken(authorization, formulario, payload.getUpc());
        } catch (FeignException.Unauthorized | FeignException.Forbidden exception) {
            // Rechazo en la capa de autenticación (antes de procesar la operación), incluso tras renovar el token
            log.error("GestoPago rechazó las credenciales. upc={} status={}", payload.getUpc(), exception.status());
            return ResultadoSendTx.noEnviada(ErrorCode.AUTHENTICATION_ERROR);
        } catch (IntegrationException exception) {
            log.error("No se pudo renovar el token de GestoPago. upc={}", payload.getUpc());
            return ResultadoSendTx.noEnviada(exception.getErrorCode());
        } catch (RuntimeException exception) {
            // Otros FeignException (status HTTP o RetryableException de E/S) y cualquier falla inesperada
            return clasificarFalla(exception, payload.getUpc());
        }

        return interpretarRespuesta(respuesta, payload.getUpc());
    }

    /** El token dura 24 h: si GestoPago responde 401/403 (INVALID/EXPIRED) se renueva y se reintenta UNA vez. */
    private byte[] enviarConRenovacionDeToken(String authorization, MultiValueMap<String, String> formulario, String upc) {
        try {
            return sendTxClient.enviarTransaccion(authorization, apiKey, formulario);
        } catch (FeignException.Unauthorized | FeignException.Forbidden exception) {
            // Seguro de reintentar: la petición se rechazó en la autenticación, la operación no se procesó
            log.warn("GestoPago respondió {} (token inválido o expirado). Se renueva y se reintenta una vez. upc={}",
                    exception.status(), upc);
            tokenService.renovarToken();
            return sendTxClient.enviarTransaccion(authorization(obtenerToken()), apiKey, formulario);
        }
    }

    private ResultadoSendTx clasificarFalla(RuntimeException exception, String upc) {
        if (exception instanceof FeignException feign && feign.status() > 0) {
            int status = feign.status();
            // 4xx: GestoPago rechazó la petición sin procesarla. 5xx (incluido 504 de CloudFront): pudo procesarse
            if (status >= 400 && status < 500 && status != 408) {
                log.error("sendTx rechazado por HTTP {} (no procesado). upc={}", status, upc);
                return ResultadoSendTx.noEnviada(ErrorCode.HTTP_ERROR);
            }
            log.error("sendTx respondió HTTP {}: desenlace INDETERMINADO, se concilia. upc={}", status, upc);
            return ResultadoSendTx.indeterminada(null, null,
                    status == 504 || status == 408 ? ErrorCode.TIMEOUT_ERROR : ErrorCode.HTTP_ERROR);
        }
        if (fallaAntesDeEnviar(exception)) {
            log.error("No se pudo conectar con GestoPago (no enviado). upc={} causa={}", upc, causaRaiz(exception));
            return ResultadoSendTx.noEnviada(ErrorCode.COMMUNICATION_ERROR);
        }
        boolean timeout = contieneCausa(exception, SocketTimeoutException.class);
        log.error("Falla durante sendTx: desenlace INDETERMINADO, se concilia. upc={} causa={}", upc, causaRaiz(exception));
        return ResultadoSendTx.indeterminada(null, null, timeout ? ErrorCode.TIMEOUT_ERROR : ErrorCode.COMMUNICATION_ERROR);
    }

    /** Fallas en la fase de conexión: DNS, conexión rechazada, timeout de conexión o handshake TLS. */
    private boolean fallaAntesDeEnviar(Throwable exception) {
        Throwable actual = exception;
        while (actual != null) {
            if (actual instanceof ConnectException
                    || actual instanceof UnknownHostException
                    || actual instanceof NoRouteToHostException
                    || actual instanceof SSLHandshakeException) {
                return true;
            }
            if (actual instanceof SocketTimeoutException
                    && actual.getMessage() != null
                    && actual.getMessage().toLowerCase(Locale.ROOT).contains("connect")) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }

    private boolean contieneCausa(Throwable exception, Class<? extends Throwable> tipo) {
        Throwable actual = exception;
        while (actual != null) {
            if (tipo.isInstance(actual)) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }

    /** Solo el tipo de la causa: los mensajes de Feign incluyen la URL y podrían filtrar datos. */
    private String causaRaiz(Throwable exception) {
        Throwable actual = exception;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        return actual.getClass().getSimpleName();
    }

    // ---------------------------------------------------------------- token y payload

    /** Reutiliza el token vigente; solo solicita uno nuevo si aún no existe. */
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

    private String authorization(GestoPagoToken token) {
        return "Bearer " + token.getToken().trim();
    }

    private String serializar(SendTxPayload payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.error("No se pudo serializar el payload de sendTx");
            throw new IntegrationException(ErrorCode.INTERNAL_ERROR, exception);
        }
    }

    // ---------------------------------------------------------------- respuesta XML

    /** GestoPago responde HTTP 200 tanto en éxito como en error: lo único que decide es el nodo CODIGO del XML. */
    private ResultadoSendTx interpretarRespuesta(byte[] xml, String upc) {
        if (xml == null || xml.length == 0) {
            log.error("sendTx respondió vacío: desenlace INDETERMINADO. upc={}", upc);
            return ResultadoSendTx.indeterminada(null, null, ErrorCode.INVALID_RESPONSE);
        }
        String codigo;
        String texto;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Protección contra XXE: la respuesta viene de un tercero
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document documento = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            codigo = textoDelNodo(documento, "CODIGO");
            texto = textoDelNodo(documento, "TEXTO");
        } catch (Exception exception) {
            log.error("La respuesta de sendTx no es un XML válido: desenlace INDETERMINADO. upc={}", upc);
            return ResultadoSendTx.indeterminada(null, null, ErrorCode.INVALID_RESPONSE);
        }

        if (codigo == null || codigo.isEmpty()) {
            log.error("La respuesta de sendTx no contiene CODIGO: desenlace INDETERMINADO. upc={}", upc);
            return ResultadoSendTx.indeterminada(null, texto, ErrorCode.INVALID_RESPONSE);
        }
        if (CODIGO_EXITO.equals(codigo)) {
            return ResultadoSendTx.aprobada(codigo, texto);
        }
        if (codigosIndeterminados.contains(codigo)) {
            log.warn("sendTx respondió un código sin resultado definitivo: INDETERMINADO. upc={} codigo={}", upc, codigo);
            return ResultadoSendTx.indeterminada(codigo, texto, ErrorCode.TIMEOUT_ERROR);
        }
        log.warn("Pago rechazado por GestoPago. upc={} codigo={}", upc, codigo);
        return ResultadoSendTx.rechazada(codigo, texto);
    }

    private String textoDelNodo(Document documento, String nombre) {
        NodeList nodos = documento.getElementsByTagName(nombre);
        return nodos.getLength() == 0 ? null : nodos.item(0).getTextContent().trim();
    }
}
