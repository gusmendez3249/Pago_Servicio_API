package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.EstadoPagoRequest;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.mapper.PagoServicioMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Consulta del estado de un pago propio por upc o por Idempotency-Key. Una operación que no existe o es de otro usuario
 * responde exactamente igual (404 OPERACION_NO_ENCONTRADA). Si está INDETERMINADA y ya procede, se consulta confirmTx
 * ({@link ConciliacionPagoService#resolverConProveedor}) antes de responder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstadoPagoService {

    private final SesionService sesionService;
    private final TransaccionPagoService transaccionPagoService;
    private final ConciliacionPagoService conciliacionPagoService;
    private final PagoServicioMapper mapper;

    public PagoServicioResponse consultar(String authorization, EstadoPagoRequest request) {
        SesionUsuario sesion = sesionService.autenticar(authorization);
        if (sesion.esEjecutivo() || sesion.clienteId() == null) {
            throw new AccesoDenegadoException("Solo el titular de la cuenta puede consultar el estado de sus pagos.",
                    MotivoPago.NO_TITULAR);
        }
        TransaccionPagoEntity tx = buscar(request)
                .filter(t -> Objects.equals(t.getUsuarioId(), sesion.usuarioId()))
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la operación indicada.",
                        MotivoPago.OPERACION_NO_ENCONTRADA));

        if (tx.getEstatus() == EstatusTransaccionPago.INDETERMINADA) {
            conciliacionPagoService.resolverConProveedor(tx);
            // Se relee para responder con el estado real (la consulta pudo confirmarla o rechazarla)
            tx = transaccionPagoService.buscarPorUpc(tx.getUpc()).orElse(tx);
        }
        CuentaEntity cuenta = tx.getCuenta();
        return mapper.aRespuesta(tx, cuenta.getNumeroCuenta(), transaccionPagoService.saldo(cuenta.getId()));
    }

    private Optional<TransaccionPagoEntity> buscar(EstadoPagoRequest request) {
        if (request.getUpc() != null) {
            return transaccionPagoService.buscarPorUpc(request.getUpc());
        }
        return transaccionPagoService.buscarPorLlave(request.getIdempotencyKey().toLowerCase(Locale.ROOT));
    }
}
