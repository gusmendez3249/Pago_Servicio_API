package com.proyecto.servicios.service;

import com.proyecto.servicios.client.SendTxClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx.Desenlace;
import com.proyecto.servicios.model.gestopago.SendTxPayload;
import com.proyecto.servicios.service.Impl.SendTxServiceImpl;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Clasificación de cada respuesta o falla de sendTx: solo hay "no enviada" cuando es seguro. */
@ExtendWith(MockitoExtension.class)
class SendTxServiceImplTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.POST,
            "https://gestopago.portalventas.net/sistema/service/sendTx.do", Map.of(), null, StandardCharsets.UTF_8, null);

    @Mock private SendTxClient sendTxClient;
    @Mock private GestoPagoTokenService tokenService;
    @Mock private CifradoService cifradoService;

    private SendTxServiceImpl service;
    private final SendTxPayload payload = SendTxPayload.builder().idServicio(1).idProducto(2)
            .montoPago(new BigDecimal("100.00")).telefono("4181234567").upc("ABCDE12345").build();

    @BeforeEach
    void setUp() {
        service = new SendTxServiceImpl(sendTxClient, tokenService, cifradoService, 83, "DISPOSITIVO", "", "82");
    }

    private void tokenYCifradoOk() {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("token-vigente");
        lenient().when(tokenService.obtenerTokenActivo(83, "DISPOSITIVO")).thenReturn(Optional.of(token));
        lenient().when(cifradoService.cifrar(anyString())).thenReturn("cifrado");
    }

    private static byte[] xml(String codigo) {
        return ("<RESPONSE><CODIGO>" + codigo + "</CODIGO><TEXTO>texto</TEXTO></RESPONSE>").getBytes(StandardCharsets.UTF_8);
    }

    private static FeignException http(int status) {
        Response response = Response.builder().status(status).reason("r").request(REQUEST)
                .headers(Map.of()).body(new byte[0]).build();
        return FeignException.errorStatus("enviarTransaccion", response);
    }

    private static RetryableException io(Throwable causa) {
        return new RetryableException(-1, causa.getMessage(), Request.HttpMethod.POST, causa, (Long) null, REQUEST);
    }

    @Test
    void codigo01_Aprobada() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(eq("Bearer token-vigente"), isNull(), any())).thenReturn(xml("01"));

        ResultadoSendTx r = service.enviar(payload);
        assertEquals(Desenlace.APROBADA, r.desenlace());
        assertEquals("01", r.codigo());
    }

    @Test
    void codigoDeRechazo_Rechazada() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenReturn(xml("05"));

        assertEquals(Desenlace.RECHAZADA, service.enviar(payload).desenlace());
    }

    @Test
    void codigo82_Indeterminada() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenReturn(xml("82"));

        assertEquals(Desenlace.INDETERMINADA, service.enviar(payload).desenlace());
    }

    @ParameterizedTest
    @ValueSource(strings = {"no es xml", "<RESPONSE><TEXTO>sin codigo</TEXTO></RESPONSE>", ""})
    void respuestaIlegible_Indeterminada(String cuerpo) {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenReturn(cuerpo.getBytes(StandardCharsets.UTF_8));

        assertEquals(Desenlace.INDETERMINADA, service.enviar(payload).desenlace());
    }

    @Test
    void xmlConDoctype_SeRechazaPorXXE_Indeterminada() {
        tokenYCifradoOk();
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><R><CODIGO>&x;</CODIGO></R>";
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenReturn(xxe.getBytes(StandardCharsets.UTF_8));

        assertEquals(Desenlace.INDETERMINADA, service.enviar(payload).desenlace());
    }

    @Test
    void timeoutDeLectura_Indeterminada_NoSeReintenta() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(io(new SocketTimeoutException("Read timed out")));

        ResultadoSendTx r = service.enviar(payload);
        assertEquals(Desenlace.INDETERMINADA, r.desenlace());
        assertEquals(ErrorCode.TIMEOUT_ERROR, r.error());
        verify(sendTxClient, times(1)).enviarTransaccion(any(), any(), any());
    }

    @Test
    void conexionRechazada_NoEnviada() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(io(new ConnectException("Connection refused")));

        assertEquals(Desenlace.NO_ENVIADA, service.enviar(payload).desenlace());
    }

    @Test
    void timeoutDeConexion_NoEnviada() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(io(new SocketTimeoutException("Connect timed out")));

        assertEquals(Desenlace.NO_ENVIADA, service.enviar(payload).desenlace());
    }

    @Test
    void dnsNoResuelve_NoEnviada() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(io(new UnknownHostException("host")));

        assertEquals(Desenlace.NO_ENVIADA, service.enviar(payload).desenlace());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void error5xx_Indeterminada(int status) {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(http(status));

        assertEquals(Desenlace.INDETERMINADA, service.enviar(payload).desenlace());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 415, 429})
    void error4xx_NoEnviada(int status) {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(http(status));

        assertEquals(Desenlace.NO_ENVIADA, service.enviar(payload).desenlace());
    }

    @Test
    void tokenExpirado_SeRenuevaYSeReintentaUnaVez() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(http(403)).thenReturn(xml("01"));

        assertEquals(Desenlace.APROBADA, service.enviar(payload).desenlace());
        verify(tokenService).renovarToken();
        verify(sendTxClient, times(2)).enviarTransaccion(any(), any(), any());
    }

    @Test
    void credencialesRechazadasTrasRenovar_NoEnviada() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(http(401));

        ResultadoSendTx r = service.enviar(payload);
        assertEquals(Desenlace.NO_ENVIADA, r.desenlace());
        assertEquals(ErrorCode.AUTHENTICATION_ERROR, r.error());
        verify(sendTxClient, times(2)).enviarTransaccion(any(), any(), any());
    }

    @Test
    void cifradoSinConfigurar_NoEnviada_SinLlamarAlProveedor() {
        when(cifradoService.cifrar(anyString())).thenThrow(new IntegrationException(ErrorCode.CONFIGURATION_ERROR));

        ResultadoSendTx r = service.enviar(payload);
        assertEquals(Desenlace.NO_ENVIADA, r.desenlace());
        assertEquals(ErrorCode.CONFIGURATION_ERROR, r.error());
        verifyNoInteractions(sendTxClient);
    }

    @Test
    void sinToken_NoEnviada() {
        when(cifradoService.cifrar(anyString())).thenReturn("cifrado");
        when(tokenService.obtenerTokenActivo(83, "DISPOSITIVO")).thenReturn(Optional.empty());

        assertEquals(Desenlace.NO_ENVIADA, service.enviar(payload).desenlace());
        verifyNoInteractions(sendTxClient);
    }
}
