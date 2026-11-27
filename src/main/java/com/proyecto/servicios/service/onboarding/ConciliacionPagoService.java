package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.ConciliacionPagoRequest;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.OperacionDuplicadaException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.mapper.PagoServicioMapper;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.service.ConsultaEstadoPagoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Conciliación de pagos cuyo resultado no se conoce:
 * - PENDIENTE antiguos (la aplicación se detuvo durante el envío) pasan a INDETERMINADA;
 * - INDETERMINADA se consulta al proveedor con confirmTx ({@link #resolverConProveedor}); si confirma, se cierra
 *   (CONFIRMADA con folio, o RECHAZADA con un solo reintegro);
 * - si no es posible determinarlo, el monto sigue retenido y un ejecutivo la resuelve manualmente.
 * Nunca se reenvía sendTx para averiguar el resultado y nunca se consulta una operación PENDIENTE.
 */
@Slf4j
@Service
public class ConciliacionPagoService {

    /** confirmTx no se consulta antes de 62 s: es lo que GestoPago espera al operador (CODIGO 82). */
    static final long ESPERA_MINIMA_SEGUNDOS = 62;
    /** Después de 24 h el proveedor ya no sirve para confirmar: queda para conciliación manual. */
    static final long VENTANA_MAXIMA_HORAS = 24;
    /** Separación mínima entre dos consultas de la misma operación. */
    static final long INTERVALO_MINIMO_SEGUNDOS = 30;

    private final TransaccionPagoService transaccionPagoService;
    private final ConsultaEstadoPagoService consultaEstadoPagoService;
    private final SesionService sesionService;
    private final PagoServicioMapper mapper;
    private final long minutosPendiente;
    private final ZoneId zona;

    public ConciliacionPagoService(
            TransaccionPagoService transaccionPagoService,
            ConsultaEstadoPagoService consultaEstadoPagoService,
            SesionService sesionService,
            PagoServicioMapper mapper,
            @Value("${gestopago.conciliacion.minutos-pendiente:5}") long minutosPendiente,
            @Value("${gestopago.sendtx.zone:America/Mexico_City}") String zona) {
        this.transaccionPagoService = transaccionPagoService;
        this.consultaEstadoPagoService = consultaEstadoPagoService;
        this.sesionService = sesionService;
        this.mapper = mapper;
        this.minutosPendiente = minutosPendiente;
        this.zona = ZoneId.of(zona);
    }

    public void conciliar() {
        // Las fechas de las operaciones se guardan en la zona de gestopago.sendtx.zone: se compara en la misma zona
        LocalDateTime ahora = LocalDateTime.now(zona);

        // El umbral supera el timeout de lectura de sendTx (65 s): una petición en curso nunca se toca aquí
        for (TransaccionPagoEntity tx : transaccionPagoService.buscarAnteriores(
                EstatusTransaccionPago.PENDIENTE, ahora.minusMinutes(minutosPendiente))) {
            if (transaccionPagoService.marcarIndeterminada(tx.getId(), null,
                    "Sin resultado: el procesamiento se interrumpió durante el envío")) {
                log.warn("Pago PENDIENTE abandonado pasa a INDETERMINADA. upc={}", tx.getUpc());
            }
        }

        for (TransaccionPagoEntity tx : transaccionPagoService.buscarAnteriores(EstatusTransaccionPago.INDETERMINADA, ahora)) {
            try {
                resolverConProveedor(tx);
            } catch (RuntimeException exception) {
                // Una operación con problemas no debe impedir conciliar las demás
                log.error("Falló la conciliación de una operación. upc={} causa={}", tx.getUpc(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * Consulta confirmTx de UNA operación INDETERMINADA y la cierra según la respuesta: CODIGO 06 la confirma (con folio) y
     * 70 la rechaza reintegrando el monto una sola vez; cualquier otra cosa la deja INDETERMINADA (monto retenido).
     * Solo consulta si ya pasaron 62 s desde la reserva, no han pasado 24 h, la operación guardó los parámetros de sendTx y
     * pasaron 30 s desde la última consulta; el reclamo es atómico, así que el planificador y /pagos/estado no consultan
     * a la vez. Nunca consulta una operación PENDIENTE.
     */
    public void resolverConProveedor(TransaccionPagoEntity tx) {
        if (tx.getEstatus() != EstatusTransaccionPago.INDETERMINADA) {
            return;
        }
        if (tx.getPayloadSendtx() == null || tx.getPayloadSendtx().isBlank()) {
            log.debug("Pago INDETERMINADO sin parámetros de sendTx guardados: conciliación manual. upc={}", tx.getUpc());
            return;
        }
        LocalDateTime ahora = LocalDateTime.now(zona);
        LocalDateTime desde = tx.getFechaTransaccion();
        if (ahora.isBefore(desde.plusSeconds(ESPERA_MINIMA_SEGUNDOS))) {
            return;
        }
        if (!ahora.isBefore(desde.plusHours(VENTANA_MAXIMA_HORAS))) {
            log.debug("Pago INDETERMINADO fuera de la ventana de confirmTx: conciliación manual. upc={}", tx.getUpc());
            return;
        }
        if (!transaccionPagoService.reclamarConsultaConfirmacion(tx.getId(), ahora,
                ahora.minusSeconds(INTERVALO_MINIMO_SEGUNDOS))) {
            return;
        }

        Optional<ResultadoSendTx> resultado = consultaEstadoPagoService.consultar(tx);
        if (resultado.isEmpty()) {
            log.warn("Pago INDETERMINADO sigue sin resultado (monto retenido). upc={} desde={}", tx.getUpc(),
                    tx.getFechaTransaccion());
            return;
        }
        ResultadoSendTx r = resultado.get();
        switch (r.desenlace()) {
            case APROBADA -> {
                if (transaccionPagoService.confirmar(tx.getId(), r.codigo(), r.texto(), r.datos())) {
                    log.info("Pago INDETERMINADO confirmado por confirmTx. upc={}", tx.getUpc());
                }
            }
            case RECHAZADA -> {
                if (transaccionPagoService.rechazarYReintegrar(tx.getId(), tx.getCuenta().getId(), tx.getMonto(),
                        r.codigo(), r.texto(), MotivoPago.RECHAZADO_PROVEEDOR)) {
                    log.info("Pago INDETERMINADO rechazado por confirmTx: monto reintegrado. upc={}", tx.getUpc());
                }
            }
            default -> {
                // aún sin resultado definitivo
            }
        }
    }

    /** Un ejecutivo cierra un pago INDETERMINADO según el reporte del proveedor. */
    public PagoServicioResponse resolverManual(String authorization, ConciliacionPagoRequest request) {
        SesionUsuario sesion = sesionService.autenticar(authorization);
        if (!sesion.esEjecutivo()) {
            throw new AccesoDenegadoException("Solo un ejecutivo puede conciliar operaciones.");
        }
        TransaccionPagoEntity tx = transaccionPagoService.buscarPorUpc(request.getUpc())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una operación con el upc indicado."));
        if (tx.getEstatus() != EstatusTransaccionPago.INDETERMINADA) {
            throw new OperacionDuplicadaException("La operación no está pendiente de conciliación (estatus "
                    + tx.getEstatus() + ").");
        }

        CuentaEntity cuenta = tx.getCuenta();
        String texto = "Conciliada manualmente por " + sesion.username();
        boolean aplicada = Boolean.TRUE.equals(request.getAplicada());
        boolean resuelta = aplicada
                ? transaccionPagoService.confirmar(tx.getId(), null, texto)
                : transaccionPagoService.rechazarYReintegrar(tx.getId(), cuenta.getId(), tx.getMonto(), null, texto,
                MotivoPago.RECHAZADO_PROVEEDOR);
        if (!resuelta) {
            throw new OperacionDuplicadaException("La operación ya fue resuelta por otro proceso.");
        }
        log.info("Pago conciliado manualmente. upc={} aplicada={} usuarioId={}", tx.getUpc(), aplicada, sesion.usuarioId());

        // Misma construcción de respuesta que el resto de los pagos (mapper): el estatus recién resuelto se aplica en memoria
        tx.setEstatus(aplicada ? EstatusTransaccionPago.CONFIRMADA : EstatusTransaccionPago.RECHAZADA);
        tx.setMotivo(aplicada ? null : MotivoPago.RECHAZADO_PROVEEDOR);
        return mapper.aRespuesta(tx, cuenta.getNumeroCuenta(), transaccionPagoService.saldo(cuenta.getId()));
    }
}
