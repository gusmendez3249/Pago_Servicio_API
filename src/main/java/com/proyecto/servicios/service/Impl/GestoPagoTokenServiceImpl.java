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

import java.time.LocalDateTime;
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

    /** Vigencia que se le da al token guardado: GestoPago lo emite por 24 h; se renueva un poco antes. */
    @Value("${gestopago.auth.vigencia-minutos:1410}")
    private long vigenciaMinutos = 1410;

    /** Tiempo mínimo entre dos solicitudes de token: evita pedirlo en ráfaga aunque algo falle. */
    @Value("${gestopago.auth.minutos-entre-solicitudes:10}")
    private long minutosEntreSolicitudes = 10;

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

    /**
     * Revisión periódica (al arrancar y cada hora): solo solicita un token si no hay uno guardado o si el guardado
     * ya cumplió su vigencia. GestoPago emite tokens de 24 h y su documentación pide tomar uno solo
     * por día, así que reiniciar la aplicación NO debe pedir uno nuevo.
     */
    @Scheduled(fixedRateString = "${gestopago.auth.refresh-rate-ms:3600000}", initialDelay = 0)
    public void renovarSiHaceFalta() {
        Optional<GestoPagoToken> guardado = tokenRepository
                .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);
        if (guardado.filter(this::vigente).isPresent()) {
            log.info("Token GestoPago vigente: no se solicita uno nuevo");
            return;
        }
        renovarToken();
    }

    /**
     * Solicita un token nuevo a GestoPago. Solo debe llamarse cuando no hay token vigente o cuando GestoPago
     * rechazó el actual (401/403). Si ya se solicitó uno hace muy poco, se reutiliza ese y no se vuelve a pedir.
     */
    @Override
    public synchronized void renovarToken() {
        Optional<GestoPagoToken> guardado = tokenRepository
                .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);
        if (guardado.filter(this::recienSolicitado).isPresent()) {
            log.warn("Se omitió la solicitud de token GestoPago: ya se pidió uno hace menos de {} min",
                    minutosEntreSolicitudes);
            return;
        }
        log.info("Solicitando token GestoPago para distribuidor={}", idDistribuidor);
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
                    // Explícito: la vigencia se cuenta desde aquí aunque el token recibido sea igual al guardado
                    existente.setFechaActualizacion(LocalDateTime.now());
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
                .filter(token -> Boolean.TRUE.equals(token.getActivo()))
                .filter(this::vigente);
    }

    private boolean vigente(GestoPagoToken token) {
        return token.getToken() != null && !token.getToken().isBlank()
                && token.getFechaActualizacion() != null
                && token.getFechaActualizacion().plusMinutes(vigenciaMinutos).isAfter(LocalDateTime.now());
    }

    private boolean recienSolicitado(GestoPagoToken token) {
        return token.getToken() != null && !token.getToken().isBlank()
                && token.getFechaActualizacion() != null
                && token.getFechaActualizacion().plusMinutes(minutosEntreSolicitudes).isAfter(LocalDateTime.now());
    }
}
