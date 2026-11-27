package com.proyecto.servicios.service;

import com.proyecto.servicios.client.VerifyReferenceClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.model.gestopago.ResultadoVerifyReference;
import com.proyecto.servicios.model.gestopago.VerifyReferencePayload;
import com.proyecto.servicios.service.Impl.VerifyReferenceServiceImpl;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Interpretación de verifyReference: solo CODIGO 01 con SALDO numérico y 02 son resultados; lo demás es falla. */
@ExtendWith(MockitoExtension.class)
class VerifyReferenceServiceImplTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.POST,
            "https://gestopago.portalventas.net/sistema/service/verifyReference.do", Map.of(), null, StandardCharsets.UTF_8, null);

    @Mock private VerifyReferenceClient client;
    @Mock private GestoPagoTokenService tokenService;
    @Mock private CifradoService cifradoService;

    private VerifyReferenceServiceImpl service;
    private final VerifyReferencePayload payload = new VerifyReferencePayload(2, 1, "REF123");

    @BeforeEach
    void setUp() {
        service = new VerifyReferenceServiceImpl(client, tokenService, cifradoService, 83, "DISPOSITIVO", " clave ");
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("jwt-vigente");
        lenient().when(tokenService.obtenerTokenActivo(83, "DISPOSITIVO")).thenReturn(Optional.of(token));
        lenient().when(cifradoService.cifrar(anyString())).thenReturn("cifrado");
    }

    private static byte[] xml(String cuerpo) {
        return ("<RESPONSE>" + cuerpo + "</RESPONSE>").getBytes(StandardCharsets.UTF_8);
    }

    private static FeignException http(int status) {
        Response response = Response.builder().status(status).reason("r").request(REQUEST)
                .headers(Map.of()).body(new byte[0]).build();
        return FeignException.errorStatus("verificarReferencia", response);
    }

    private void respondeXml(String cuerpo) {
        when(client.verificarReferencia(anyString(), any(), any())).thenReturn(xml(cuerpo));
    }

    private HttpStatus fallaCon(RuntimeException error) {
        when(client.verificarReferencia(anyString(), any(), any())).thenThrow(error);
        return assertThrows(ProveedorNoDisponibleException.class, () -> service.verificar(payload)).getStatus();
    }

    private HttpStatus estadoDeLaFalla() {
        return assertThrows(ProveedorNoDisponibleException.class, () -> service.verificar(payload)).getStatus();
    }

    // ---------------------------------------------------------------- resultados

    @Test
    void codigo01ConSaldo_Valida() {
        respondeXml("<CODIGO>01</CODIGO><TEXTO>ok</TEXTO><SALDO>350.00</SALDO>");

        ResultadoVerifyReference r = service.verificar(payload);

        assertTrue(r.valida());
        assertEquals(new BigDecimal("350.00"), r.saldo());
    }

    @Test
    void saldoNegativo_SeConserva() {
        respondeXml("<CODIGO>01</CODIGO><TEXTO>ok</TEXTO><SALDO>-50</SALDO>");
        assertEquals(new BigDecimal("-50"), service.verificar(payload).saldo());
    }

    @Test
    void codigo02_InvalidaSinSaldo() {
        respondeXml("<CODIGO>02</CODIGO><TEXTO>Referencia inexistente</TEXTO>");

        ResultadoVerifyReference r = service.verificar(payload);

        assertFalse(r.valida());
        assertNull(r.saldo());
    }

    @Test
    @SuppressWarnings("unchecked")
    void envioCifradoConTokenYApiKey() {
        respondeXml("<CODIGO>02</CODIGO><TEXTO>x</TEXTO>");

        service.verificar(payload);

        ArgumentCaptor<MultiValueMap<String, String>> formulario = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(client).verificarReferencia(eq("Bearer jwt-vigente"), eq("clave"), formulario.capture());
        assertEquals("cifrado", formulario.getValue().getFirst("signed"));
        assertNotNull(formulario.getValue().getFirst("random"));
        verify(cifradoService).cifrar("{\"idProducto\":2,\"idServicio\":1,\"referencia\":\"REF123\"}");
    }

    // ---------------------------------------------------------------- fallas: nunca hay resultado

    @ParameterizedTest
    @ValueSource(strings = {
            "<CODIGO>99</CODIGO><TEXTO>x</TEXTO>",
            "<CODIGO>82</CODIGO>",
            "<TEXTO>sin codigo</TEXTO>",
            "<CODIGO>01</CODIGO><TEXTO>ok</TEXTO>",
            "<CODIGO>01</CODIGO><SALDO></SALDO>",
            "<CODIGO>01</CODIGO><SALDO>abc</SALDO>",
            "<CODIGO>01</CODIGO><SALDO>1,000.00</SALDO>"
    })
    void respuestaNoValida_502(String cuerpo) {
        respondeXml(cuerpo);

        assertEquals(HttpStatus.BAD_GATEWAY, estadoDeLaFalla());
    }

    @Test
    void xmlIlegible_502() {
        when(client.verificarReferencia(anyString(), any(), any())).thenReturn("no es xml".getBytes(StandardCharsets.UTF_8));

        assertEquals(HttpStatus.BAD_GATEWAY, estadoDeLaFalla());
    }

    @Test
    void respuestaVacia_502() {
        when(client.verificarReferencia(anyString(), any(), any())).thenReturn(new byte[0]);

        assertEquals(HttpStatus.BAD_GATEWAY, estadoDeLaFalla());
    }

    @Test
    void xmlConDoctype_RechazadoPorXxe_502() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<RESPONSE><CODIGO>01</CODIGO><SALDO>&x;</SALDO></RESPONSE>";
        when(client.verificarReferencia(anyString(), any(), any())).thenReturn(xxe.getBytes(StandardCharsets.UTF_8));

        assertEquals(HttpStatus.BAD_GATEWAY, estadoDeLaFalla());
    }

    @Test
    void timeoutDeLectura_504() {
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, fallaCon(new RetryableException(-1, "timeout",
                Request.HttpMethod.POST, new SocketTimeoutException("Read timed out"), (Long) null, REQUEST)));
    }

    @ParameterizedTest
    @ValueSource(ints = {504, 408})
    void httpDeTiempoAgotado_504(int status) {
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, fallaCon(http(status)));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 500, 502, 503})
    void erroresHttp_502(int status) {
        assertEquals(HttpStatus.BAD_GATEWAY, fallaCon(http(status)));
    }

    @Test
    void falloDeConexion_502() {
        assertEquals(HttpStatus.BAD_GATEWAY, fallaCon(new RetryableException(-1, "conn",
                Request.HttpMethod.POST, new ConnectException("rechazada"), (Long) null, REQUEST)));
    }

    // ---------------------------------------------------------------- token y configuración

    @Test
    void tokenRechazado_RenuevaYReintentaUnaVez() {
        when(client.verificarReferencia(anyString(), any(), any()))
                .thenThrow(http(401)).thenReturn(xml("<CODIGO>02</CODIGO>"));

        assertFalse(service.verificar(payload).valida());
        verify(tokenService).renovarToken();
        verify(client, times(2)).verificarReferencia(anyString(), any(), any());
    }

    @Test
    void tokenRechazadoTrasRenovar_502() {
        when(client.verificarReferencia(anyString(), any(), any())).thenThrow(http(403));

        assertEquals(HttpStatus.BAD_GATEWAY, estadoDeLaFalla());
        verify(client, times(2)).verificarReferencia(anyString(), any(), any());
    }

    @Test
    void errorDeCifradoPorConfiguracion_500SinLlamarAlProveedor() {
        when(cifradoService.cifrar(anyString())).thenThrow(new IntegrationException(ErrorCode.CONFIGURATION_ERROR));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, estadoDeLaFalla());
        verifyNoInteractions(client);
    }

    @Test
    void configuracionDeGestoPagoIncompleta_500() {
        VerifyReferenceServiceImpl sinDispositivo = new VerifyReferenceServiceImpl(client, tokenService, cifradoService, 83, " ", "");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, assertThrows(ProveedorNoDisponibleException.class,
                () -> sinDispositivo.verificar(payload)).getStatus());
        verifyNoInteractions(client);
    }

    @Test
    void sinTokenDisponible_502() {
        when(tokenService.obtenerTokenActivo(83, "DISPOSITIVO")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.BAD_GATEWAY, estadoDeLaFalla());
        verifyNoInteractions(client);
    }

    @Test
    void payloadNoImprimeLaReferencia() {
        assertFalse(payload.toString().contains("REF123"));
    }
}
