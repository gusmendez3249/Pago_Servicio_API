package com.proyecto.servicios.service;

import com.proyecto.servicios.client.SendTxClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.enums.ErrorCode;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx.Desenlace;
import com.proyecto.servicios.model.gestopago.SendTxPayload;
import com.proyecto.servicios.service.Impl.SendTxServiceImpl;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Casos borde de sendTx: configuración, token, códigos configurables y fallas no previstas. */
@ExtendWith(MockitoExtension.class)
class SendTxServiceImplCasosBordeTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.POST,
            "https://gestopago.portalventas.net/sistema/service/sendTx.do", Map.of(), null, StandardCharsets.UTF_8, null);

    @Mock private SendTxClient sendTxClient;
    @Mock private GestoPagoTokenService tokenService;
    @Mock private CifradoService cifradoService;

    private final SendTxPayload payload = SendTxPayload.porTelefono(2, 1, "4181234567",
            new BigDecimal("100.00"), "20260101120000", "ABCDE12345", null);

    private SendTxServiceImpl servicio(Integer distribuidor, String dispositivo, String apiKey, String indeterminados) {
        return new SendTxServiceImpl(sendTxClient, tokenService, cifradoService, distribuidor, dispositivo, apiKey, indeterminados);
    }

    private SendTxServiceImpl servicio() {
        return servicio(83, "DISPOSITIVO", "", "82");
    }

    private static GestoPagoToken token(String valor) {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken(valor);
        return token;
    }

    private void tokenYCifradoOk() {
        when(tokenService.obtenerTokenActivo(83, "DISPOSITIVO")).thenReturn(Optional.of(token("token-vigente")));
        when(cifradoService.cifrar(anyString())).thenReturn("cifrado");
    }

    private static byte[] xml(String codigo) {
        return ("<RESPONSE><CODIGO>" + codigo + "</CODIGO></RESPONSE>").getBytes(StandardCharsets.UTF_8);
    }

    private static FeignException http(int status) {
        Response response = Response.builder().status(status).reason("r").request(REQUEST)
                .headers(Map.of()).body(new byte[0]).build();
        return FeignException.errorStatus("enviarTransaccion", response);
    }

    @ParameterizedTest(name = "distribuidor={0} dispositivo=[{1}]")
    @CsvSource(value = {"NULL, DISPOSITIVO", "83, NULL", "83, '  '"}, nullValues = "NULL")
    void configuracionIncompletaNoEnviaNada(Integer distribuidor, String dispositivo) {
        ResultadoSendTx r = servicio(distribuidor, dispositivo, "", "82").enviar(payload);

        assertEquals(Desenlace.NO_ENVIADA, r.desenlace());
        assertEquals(ErrorCode.CONFIGURATION_ERROR, r.error());
        verifyNoInteractions(sendTxClient, cifradoService);
    }

    @Test
    void tokenVacioNoSeEnvia() {
        when(cifradoService.cifrar(anyString())).thenReturn("cifrado");
        when(tokenService.obtenerTokenActivo(83, "DISPOSITIVO")).thenReturn(Optional.of(token("   ")));

        ResultadoSendTx r = servicio().enviar(payload);

        assertEquals(ErrorCode.AUTHENTICATION_ERROR, r.error());
        verifyNoInteractions(sendTxClient);
    }

    @Test
    void apiKeySeEnviaSinEspacios() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(eq("Bearer token-vigente"), eq("llave"), any())).thenReturn(xml("01"));

        assertEquals(Desenlace.APROBADA, servicio(83, "DISPOSITIVO", " llave ", "82").enviar(payload).desenlace());
    }

    @Test
    void codigosIndeterminadosSonConfigurables() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenReturn(xml("83"));

        assertEquals(Desenlace.INDETERMINADA, servicio(83, "DISPOSITIVO", "", "82, ,83").enviar(payload).desenlace());
    }

    @Test
    void timeoutHttp408EsIndeterminado() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(http(408));

        ResultadoSendTx r = servicio().enviar(payload);

        assertEquals(Desenlace.INDETERMINADA, r.desenlace());
        assertEquals(ErrorCode.TIMEOUT_ERROR, r.error());
    }

    @Test
    void tokenExpiradoQueNoSePuedeRenovarNoSeEnvia() {
        when(cifradoService.cifrar(anyString())).thenReturn("cifrado");
        when(tokenService.obtenerTokenActivo(83, "DISPOSITIVO"))
                .thenReturn(Optional.of(token("token-viejo")), Optional.empty());
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(http(401));

        ResultadoSendTx r = servicio().enviar(payload);

        assertEquals(Desenlace.NO_ENVIADA, r.desenlace());
        assertEquals(ErrorCode.AUTHENTICATION_ERROR, r.error());
        verify(sendTxClient, times(1)).enviarTransaccion(any(), any(), any());
    }

    @Test
    void fallaInesperadaSinCausaEsIndeterminada() {
        tokenYCifradoOk();
        when(sendTxClient.enviarTransaccion(any(), any(), any())).thenThrow(new IllegalStateException("inesperado"));

        ResultadoSendTx r = servicio().enviar(payload);

        assertEquals(Desenlace.INDETERMINADA, r.desenlace());
        assertEquals(ErrorCode.COMMUNICATION_ERROR, r.error());
    }
}
