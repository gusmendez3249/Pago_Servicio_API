package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class GestoPagoTokenServiceImpl implements GestoPagoTokenService {

    private final GestoPagoAuthClient gestoPagoAuthClient;
    private final GestoPagoTokenRepository tokenRepository;
    private final GestoPagoTokenMapper tokenMapper;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;
    private final String password;
    private final String apiKey;

    public GestoPagoTokenServiceImpl(GestoPagoAuthClient gestoPagoAuthClient,
                                     GestoPagoTokenRepository tokenRepository,
                                     GestoPagoTokenMapper tokenMapper,
                                     @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
                                     @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo,
                                     @Value("${gestopago.auth.password}") String password,
                                     @Value("${gestopago.api-key:}") String apiKey) {
        this.gestoPagoAuthClient = gestoPagoAuthClient;
        this.tokenRepository = tokenRepository;
        this.tokenMapper = tokenMapper;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
        this.password = password;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
    }

    @Override
    @Scheduled(fixedRateString = "${gestopago.auth.refresh-rate-ms:3600000}", initialDelay = 0)
    public void renovarToken() {
        log.info("Renovando token GestoPago para distribuidor={}", idDistribuidor);
        try {
            GestoPagoAuthResponse response = gestoPagoAuthClient.authenticate(
                    idDistribuidor, codigoDispositivo, password, apiKey);

            if (response == null || response.getToken() == null) {
                log.error("La respuesta de GestoPago no contiene token");
                return;
            }

            tokenRepository.save(actualizarOCrear(response));
            log.info("Token GestoPago renovado correctamente");

        } catch (Exception e) {
            // Sin e.getMessage() ni stack trace: el mensaje de Feign incluye la URL con la contraseña como parámetro
            log.error("Error al renovar token GestoPago. causa={} status={}", e.getClass().getSimpleName(),
                    e instanceof feign.FeignException feign ? feign.status() : -1);
        }
    }

    /** Reutiliza el registro del dispositivo si ya existe; si no, crea uno nuevo activo. */
    private GestoPagoToken actualizarOCrear(GestoPagoAuthResponse response) {
        return tokenRepository
                .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                .map(existente -> {
                    tokenMapper.updateEntity(response, existente);
                    return existente;
                })
                .orElseGet(() -> {
                    GestoPagoToken nuevo = tokenMapper.toEntity(response);
                    nuevo.setIdDistribuidor(idDistribuidor);
                    nuevo.setCodigoDispositivo(codigoDispositivo);
                    nuevo.setActivo(true);
                    return nuevo;
                });
    }

    @Override
    public Optional<GestoPagoToken> obtenerTokenActivo(
            Integer idDistribuidor,
            String codigoDispositivo) {

        return tokenRepository
                .findByIdDistribuidorAndCodigoDispositivo(
                        idDistribuidor,
                        codigoDispositivo
                )
                .filter(token -> Boolean.TRUE.equals(token.getActivo()));
    }
}
