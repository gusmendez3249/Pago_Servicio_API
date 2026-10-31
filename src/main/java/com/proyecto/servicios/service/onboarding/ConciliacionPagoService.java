package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.ConciliacionPagoRequest;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.OperacionDuplicadaException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.gestopago.ResultadoSendTx;
import com.proyecto.servicios.service.ConsultaEstadoPagoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Conciliación de pagos cuyo resultado no se conoce:
 * - PENDIENTE antiguos (la aplicación se detuvo durante el envío) pasan a INDETERMINADA;
 * - INDETERMINADA se consulta al proveedor; si confirma, se cierra (CONFIRMADA o RECHAZADA con reintegro);
 * - si no es posible determinarlo, el monto sigue retenido y un ejecutivo la resuelve manualmente.
 * Nunca se reenvía sendTx para averiguar el resultado.
 */
@Slf4j
@Service
public class ConciliacionPagoService {

    private final TransaccionPagoService transaccionPagoService;
    private final ConsultaEstadoPagoService consultaEstadoPagoService;
    private final SesionService sesionService;
    private final long minutosPendiente;

    public ConciliacionPagoService(
            TransaccionPagoService transaccionPagoService,
            ConsultaEstadoPagoService consultaEstadoPagoService,
            SesionService sesionService,
            @Value("${gestopago.conciliacion.minutos-pendiente:5}") long minutosPendiente) {
        this.transaccionPagoService = transaccionPagoService;
        this.consultaEstadoPagoService = consultaEstadoPagoService;
        this.sesionService = sesionService;
        this.minutosPendiente = minutosPendiente;
    }

    public void conciliar() {
        LocalDateTime ahora = LocalDateTime.now();

        // El umbral supera el timeout de lectura de sendTx (65 s): una petición en curso nunca se toca aquí
        for (TransaccionPagoEntity tx : transaccionPagoService.buscarAnteriores(
                EstatusTransaccionPago.PENDIENTE, ahora.minusMinutes(minutosPendiente))) {
            if (transaccionPagoService.marcarIndeterminada(tx.getId(), null,
                    "Sin resultado: el procesamiento se interrumpió durante el envío")) {
                log.warn("Pago PENDIENTE abandonado pasa a INDETERMINADA. upc={}", tx.getUpc());
            }
        }

        for (TransaccionPagoEntity tx : transaccionPagoService.buscarAnteriores(EstatusTransaccionPago.INDETERMINADA, ahora)) {
            Optional<ResultadoSendTx> resultado = consultaEstadoPagoService.consultar(tx.getUpc());
            if (resultado.isEmpty()) {
                log.warn("Pago INDETERMINADO pendiente de conciliación manual (monto retenido). upc={} desde={}",
                        tx.getUpc(), tx.getFechaTransaccion());
                continue;
            }
            ResultadoSendTx r = resultado.get();
            Long cuentaId = tx.getCuenta().getId();
            switch (r.desenlace()) {
                case APROBADA -> transaccionPagoService.confirmar(tx.getId(), r.codigo(), r.texto());
                case RECHAZADA -> transaccionPagoService.rechazarYReintegrar(tx.getId(), cuentaId, tx.getMonto(),
                        r.codigo(), r.texto());
                default -> {
                    // aún sin resultado definitivo
                }
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
                : transaccionPagoService.rechazarYReintegrar(tx.getId(), cuenta.getId(), tx.getMonto(), null, texto);
        if (!resuelta) {
            throw new OperacionDuplicadaException("La operación ya fue resuelta por otro proceso.");
        }
        log.info("Pago conciliado manualmente. upc={} aplicada={} usuarioId={}", tx.getUpc(), aplicada, sesion.usuarioId());

        return PagoServicioResponse.builder()
                .numCuenta(PagoServicioService.enmascarar(cuenta.getNumeroCuenta()))
                .upc(tx.getUpc())
                .estatus(aplicada ? EstatusTransaccionPago.CONFIRMADA : EstatusTransaccionPago.RECHAZADA)
                .monto(tx.getMonto())
                .saldoRestante(transaccionPagoService.saldo(cuenta.getId()))
                .mensajeRespuesta(aplicada ? "Pago confirmado por conciliación."
                        : "Pago no aplicado por el proveedor: el monto se reintegró a la cuenta.")
                .fechaTransaccion(tx.getFechaTransaccion())
                .build();
    }
}
