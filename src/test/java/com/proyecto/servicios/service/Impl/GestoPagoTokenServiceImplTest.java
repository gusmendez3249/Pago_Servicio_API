package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Renovación programada del token de GestoPago y consulta del token activo. */
@ExtendWith(MockitoExtension.class)
class GestoPagoTokenServiceImplTest {

    private static final int DISTRIBUIDOR = 100;
    private static final String DISPOSITIVO = "DISP-1";
    private static final String CLAVE = "clave-de-prueba";

    @Mock private GestoPagoAuthClient authClient;
    @Mock private GestoPagoTokenRepository repository;
    @Mock private GestoPagoTokenMapper mapper;

    private GestoPagoTokenServiceImpl servicio(String apiKey) {
        return new GestoPagoTokenServiceImpl(authClient, repository, mapper, DISTRIBUIDOR, DISPOSITIVO, CLAVE, apiKey);
    }

    private static GestoPagoAuthResponse respuesta(String token) {
        GestoPagoAuthResponse response = new GestoPagoAuthResponse();
        response.setToken(token);
        return response;
    }

    @Test
    void primeraRenovacionCreaUnTokenActivoDelDispositivo() {
        GestoPagoAuthResponse response = respuesta("jwt");
        when(authClient.authenticate(DISTRIBUIDOR, DISPOSITIVO, CLAVE, null)).thenReturn(response);
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUIDOR, DISPOSITIVO)).thenReturn(Optional.empty());
        when(mapper.toEntity(response)).thenReturn(new GestoPagoToken());

        servicio("  ").renovarToken();

        ArgumentCaptor<GestoPagoToken> guardado = ArgumentCaptor.forClass(GestoPagoToken.class);
        verify(repository).save(guardado.capture());
        assertEquals(DISTRIBUIDOR, guardado.getValue().getIdDistribuidor());
        assertEquals(DISPOSITIVO, guardado.getValue().getCodigoDispositivo());
        assertTrue(guardado.getValue().getActivo());
    }

    @Test
    void renovacionPosteriorActualizaElRegistroExistenteYEnviaApiKeySinEspacios() {
        GestoPagoAuthResponse response = respuesta("jwt");
        GestoPagoToken existente = new GestoPagoToken();
        when(authClient.authenticate(DISTRIBUIDOR, DISPOSITIVO, CLAVE, "llave")).thenReturn(response);
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUIDOR, DISPOSITIVO)).thenReturn(Optional.of(existente));

        servicio(" llave ").renovarToken();

        verify(mapper).updateEntity(response, existente);
        verify(mapper, never()).toEntity(any());
        verify(repository).save(existente);
    }

    @Test
    void respuestaSinTokenNoGuardaNada() {
        when(authClient.authenticate(anyInt(), anyString(), anyString(), isNull())).thenReturn(respuesta(null));

        servicio(null).renovarToken();

        verify(repository, never()).save(any());
    }

    @Test
    void respuestaNulaNoGuardaNada() {
        when(authClient.authenticate(anyInt(), anyString(), anyString(), isNull())).thenReturn(null);

        servicio(null).renovarToken();

        verify(repository, never()).save(any());
    }

    @Test
    void errorDeFeignNoSePropagaAlScheduler() {
        FeignException error = mock(FeignException.class);
        when(error.status()).thenReturn(401);
        when(authClient.authenticate(anyInt(), anyString(), anyString(), isNull())).thenThrow(error);

        GestoPagoTokenServiceImpl servicio = servicio(null);

        assertDoesNotThrow(servicio::renovarToken);
        verify(repository, never()).save(any());
    }

    @Test
    void errorInesperadoNoSePropagaAlScheduler() {
        when(authClient.authenticate(anyInt(), anyString(), anyString(), isNull()))
                .thenThrow(new IllegalStateException("sin red"));

        GestoPagoTokenServiceImpl servicio = servicio(null);

        assertDoesNotThrow(servicio::renovarToken);
    }

    @Test
    void obtenerTokenActivoSoloDevuelveTokensActivos() {
        GestoPagoToken activo = guardado(java.time.LocalDateTime.now().minusHours(2));
        GestoPagoToken inactivo = new GestoPagoToken();
        inactivo.setActivo(false);
        GestoPagoTokenServiceImpl servicio = servicio(null);

        when(repository.findByIdDistribuidorAndCodigoDispositivo(1, "A")).thenReturn(Optional.of(activo));
        when(repository.findByIdDistribuidorAndCodigoDispositivo(2, "B")).thenReturn(Optional.of(inactivo));

        assertSame(activo, servicio.obtenerTokenActivo(1, "A").orElseThrow());
        assertTrue(servicio.obtenerTokenActivo(2, "B").isEmpty());
    }

    // ---------------------------------------------------------------- un token cada 24 h

    private static GestoPagoToken guardado(java.time.LocalDateTime actualizado) {
        GestoPagoToken token = new GestoPagoToken();
        token.setActivo(true);
        token.setToken("token-guardado");
        token.setFechaActualizacion(actualizado);
        return token;
    }

    @Test
    void alArrancarConTokenVigenteNoSeSolicitaOtro() {
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUIDOR, DISPOSITIVO))
                .thenReturn(Optional.of(guardado(java.time.LocalDateTime.now().minusHours(5))));

        servicio(null).renovarSiHaceFalta();

        verifyNoInteractions(authClient);
        verify(repository, never()).save(any());
    }

    @Test
    void alArrancarSinTokenGuardadoSeSolicitaUno() {
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUIDOR, DISPOSITIVO)).thenReturn(Optional.empty());
        when(authClient.authenticate(DISTRIBUIDOR, DISPOSITIVO, CLAVE, null)).thenReturn(respuesta("nuevo"));
        when(mapper.toEntity(any())).thenReturn(new GestoPagoToken());

        servicio(null).renovarSiHaceFalta();

        verify(authClient, times(1)).authenticate(DISTRIBUIDOR, DISPOSITIVO, CLAVE, null);
    }

    @Test
    void conTokenVencidoSeSolicitaUnoYYaNoSeEntregaElViejo() {
        GestoPagoToken vencido = guardado(java.time.LocalDateTime.now().minusHours(24));
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUIDOR, DISPOSITIVO)).thenReturn(Optional.of(vencido));
        when(authClient.authenticate(DISTRIBUIDOR, DISPOSITIVO, CLAVE, null)).thenReturn(respuesta("nuevo"));
        GestoPagoTokenServiceImpl servicio = servicio(null);

        assertTrue(servicio.obtenerTokenActivo(DISTRIBUIDOR, DISPOSITIVO).isEmpty());
        servicio.renovarSiHaceFalta();

        verify(authClient, times(1)).authenticate(DISTRIBUIDOR, DISPOSITIVO, CLAVE, null);
    }

    @Test
    void renovacionForzadaSeOmiteSiSePidioUnTokenHaceMuyPoco() {
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUIDOR, DISPOSITIVO))
                .thenReturn(Optional.of(guardado(java.time.LocalDateTime.now().minusMinutes(2))));

        servicio(null).renovarToken();

        verifyNoInteractions(authClient);
    }

    @Test
    void renovacionForzadaSiSePermiteCuandoElTokenNoEsReciente() {
        when(repository.findByIdDistribuidorAndCodigoDispositivo(DISTRIBUIDOR, DISPOSITIVO))
                .thenReturn(Optional.of(guardado(java.time.LocalDateTime.now().minusHours(3))));
        when(authClient.authenticate(DISTRIBUIDOR, DISPOSITIVO, CLAVE, null)).thenReturn(respuesta("nuevo"));

        servicio(null).renovarToken();

        verify(authClient, times(1)).authenticate(DISTRIBUIDOR, DISPOSITIVO, CLAVE, null);
    }
}
