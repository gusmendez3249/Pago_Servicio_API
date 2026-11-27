package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.VerifyReferenceClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.model.gestopago.ResultadoVerifyReference;
import com.proyecto.servicios.model.gestopago.VerifyReferencePayload;
import com.proyecto.servicios.service.CifradoService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.VerifyReferenceService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;

/**
 * Integración con verifyReference de GestoPago. Es una consulta: no mueve dinero, así que no hay estado
 * indeterminado. Solo CODIGO 01 (con SALDO numérico) es referencia válida y 02 es referencia inválida;
 * cualquier otra cosa (timeout, error HTTP, XML ilegible, código desconocido) es una falla y nunca produce comprobante.
 */
@Slf4j
@Service
public class VerifyReferenceServiceImpl implements VerifyReferenceService {

    static final String CODIGO_VALIDA = "01";
    static final String CODIGO_INVALIDA = "02";
    private static final int MAX_TEXTO_LOG = 200;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String MENSAJE_NO_DISPONIBLE =
            "No fue posible verificar la referencia en este momento. Inténtelo más tarde.";

    private final VerifyReferenceClient client;
    private final GestoPagoTokenService tokenService;
    private final CifradoService cifradoService;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;
    private final String apiKey;

    public VerifyReferenceServiceImpl(
            VerifyReferenceClient client,
            GestoPagoTokenService tokenService,
            CifradoService cifradoService,
            @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
            @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo,
            @Value("${gestopago.api-key:}") String apiKey) {
        this.client = client;
        this.tokenService = tokenService;
        this.cifradoService = cifradoService;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
    }

    @Override
    public ResultadoVerifyReference verificar(VerifyReferencePayload payload) {
        MultiValueMap<String, String> formulario;
        String authorization;
        try {
            if (idDistribuidor == null || codigoDispositivo == null || codigoDispositivo.isBlank()) {
                log.error("Configuración de GestoPago incompleta o faltante");
                throw falla(HttpStatus.INTERNAL_SERVER_ERROR);
            }
            formulario = new LinkedMultiValueMap<>();
            formulario.add("random", String.valueOf(System.currentTimeMillis()));
            formulario.add("signed", cifradoService.cifrar(serializar(payload)));
            authorization = authorization(obtenerToken());
        } catch (IntegrationException exception) {
            log.error("No se consultó verifyReference: fallo previo al envío. causa={}", exception.getErrorCode());
            boolean interna = exception.getErrorCode() == ErrorCode.CONFIGURATION_ERROR
                    || exception.getErrorCode() == ErrorCode.INTERNAL_ERROR;
            throw falla(interna ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_GATEWAY);
        }

        byte[] respuesta;
        try {
            respuesta = consultarConRenovacionDeToken(authorization, formulario);
        } catch (FeignException.Unauthorized | FeignException.Forbidden exception) {
            log.error("GestoPago rechazó las credenciales en verifyReference. status={}", exception.status());
            throw falla(HttpStatus.BAD_GATEWAY);
        } catch (IntegrationException exception) {
            log.error("No se pudo renovar el token de GestoPago para verifyReference. causa={}", exception.getErrorCode());
            throw falla(HttpStatus.BAD_GATEWAY);
        } catch (RuntimeException exception) {
            throw clasificarFalla(exception);
        }
        return interpretarRespuesta(respuesta);
    }

    /** Si GestoPago responde 401/403 (token inválido o expirado) se renueva y se reintenta UNA vez. */
    private byte[] consultarConRenovacionDeToken(String authorization, MultiValueMap<String, String> formulario) {
        try {
            return client.verificarReferencia(authorization, apiKey, formulario);
        } catch (FeignException.Unauthorized | FeignException.Forbidden exception) {
            log.warn("GestoPago respondió {} en verifyReference. Se renueva el token y se reintenta una vez.", exception.status());
            tokenService.renovarToken();
            return client.verificarReferencia(authorization(obtenerToken()), apiKey, formulario);
        }
    }

    private ProveedorNoDisponibleException clasificarFalla(RuntimeException exception) {
        if (exception instanceof FeignException feign && feign.status() > 0) {
            int status = feign.status();
            log.error("verifyReference respondió HTTP {}", status);
            return falla(status == 504 || status == 408 ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY);
        }
        if (contieneCausa(exception, SocketTimeoutException.class)) {
            log.error("verifyReference no respondió a tiempo");
            return falla(HttpStatus.GATEWAY_TIMEOUT);
        }
        // Solo el tipo de la causa: los mensajes de Feign incluyen la URL
        log.error("Falla de comunicación en verifyReference. causa={}", causaRaiz(exception));
        return falla(HttpStatus.BAD_GATEWAY);
    }

    private static ProveedorNoDisponibleException falla(HttpStatus status) {
        return new ProveedorNoDisponibleException(MENSAJE_NO_DISPONIBLE, status);
    }

    private static boolean contieneCausa(Throwable exception, Class<? extends Throwable> tipo) {
        Throwable actual = exception;
        while (actual != null) {
            if (tipo.isInstance(actual)) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }

    private static String causaRaiz(Throwable exception) {
        Throwable actual = exception;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        return actual.getClass().getSimpleName();
    }

    // ---------------------------------------------------------------- token y payload

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
        return "Bearer " + token.getToken().trim();
    }

    private static String serializar(VerifyReferencePayload payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.error("No se pudo serializar el payload de verifyReference");
            throw new IntegrationException(ErrorCode.INTERNAL_ERROR, exception);
        }
    }

    // ---------------------------------------------------------------- respuesta XML

    private ResultadoVerifyReference interpretarRespuesta(byte[] xml) {
        if (xml == null || xml.length == 0) {
            log.error("verifyReference respondió vacío");
            throw falla(HttpStatus.BAD_GATEWAY);
        }
        String codigo;
        String texto;
        String saldoTexto;
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
            saldoTexto = textoDelNodo(documento, "SALDO");
        } catch (Exception exception) {
            log.error("La respuesta de verifyReference no es un XML válido");
            throw falla(HttpStatus.BAD_GATEWAY);
        }

        if (CODIGO_VALIDA.equals(codigo)) {
            BigDecimal saldo = parsearSaldo(saldoTexto);
            if (saldo == null) {
                log.error("verifyReference respondió CODIGO 01 sin SALDO numérico");
                throw falla(HttpStatus.BAD_GATEWAY);
            }
            log.info("verifyReference: referencia válida");
            return ResultadoVerifyReference.valida(saldo, texto);
        }
        if (CODIGO_INVALIDA.equals(codigo)) {
            // El TEXTO solo se registra: nunca se devuelve al cliente
            log.info("verifyReference: referencia inválida. texto={}", paraLog(texto));
            return ResultadoVerifyReference.invalida(texto);
        }
        log.error("verifyReference respondió un CODIGO no esperado: {}. texto={}", paraLog(codigo), paraLog(texto));
        throw falla(HttpStatus.BAD_GATEWAY);
    }

    private static BigDecimal parsearSaldo(String saldoTexto) {
        if (saldoTexto == null || saldoTexto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(saldoTexto.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String textoDelNodo(Document documento, String nombre) {
        NodeList nodos = documento.getElementsByTagName(nombre);
        return nodos.getLength() == 0 ? null : nodos.item(0).getTextContent().trim();
    }

    /** Texto de un tercero: sin saltos de línea (inyección en logs) y acotado. */
    private static String paraLog(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.replaceAll("[\\r\\n\\t]", " ");
        return limpio.length() > MAX_TEXTO_LOG ? limpio.substring(0, MAX_TEXTO_LOG) : limpio;
    }
}
