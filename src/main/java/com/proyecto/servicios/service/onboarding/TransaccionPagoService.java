package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.repositorys.onboarding.TransaccionPagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Movimientos de saldo y estatus de los pagos. Cada método es una transacción corta e independiente: ninguna
 * retiene una conexión de BD mientras se espera a GestoPago.
 *
 * Estrategia: el saldo se RESERVA (descuenta) antes de llamar al proveedor, junto con el registro PENDIENTE.
 * Si el proveedor no aplicó el pago se reintegra; si no se sabe, el monto queda retenido hasta conciliar.
 * Así nunca se paga un servicio con saldo que ya no existe ni se gasta dos veces el mismo saldo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionPagoService {

    private static final Set<EstatusTransaccionPago> ABIERTAS =
            EnumSet.of(EstatusTransaccionPago.PENDIENTE, EstatusTransaccionPago.INDETERMINADA);
    private static final int MAX_TEXTO = 500;

    private final CuentaRepository cuentaRepository;
    private final TransaccionPagoRepository transaccionPagoRepository;

    /**
     * Descuenta el saldo con un UPDATE condicionado (saldo >= monto y cuenta ACTIVA) y registra la operación
     * PENDIENTE en la misma transacción. Si la llave de idempotencia ya existe, el índice único revierte ambas cosas.
     */
    @Transactional
    public TransaccionPagoEntity reservar(Long cuentaId, TransaccionPagoEntity nueva) {
        if (cuentaRepository.descontarSaldo(cuentaId, nueva.getMonto()) == 0) {
            throw new ReglaNegocioException("Saldo insuficiente para cubrir el monto del pago o la cuenta no está activa.");
        }
        nueva.setCuenta(cuentaRepository.getReferenceById(cuentaId));
        nueva.setEstatus(EstatusTransaccionPago.PENDIENTE);
        return transaccionPagoRepository.saveAndFlush(nueva);
    }

    /** @return false si la operación ya estaba resuelta */
    @Transactional
    public boolean confirmar(Long transaccionId, String codigo, String texto) {
        return transaccionPagoRepository.cambiarEstatus(transaccionId, ABIERTAS, EstatusTransaccionPago.CONFIRMADA,
                codigo, recortar(texto), LocalDateTime.now()) == 1;
    }

    /** Marca la operación como RECHAZADA y devuelve el monto a la cuenta (solo una vez). */
    @Transactional
    public boolean rechazarYReintegrar(Long transaccionId, Long cuentaId, BigDecimal monto, String codigo, String texto) {
        int cambiadas = transaccionPagoRepository.cambiarEstatus(transaccionId, ABIERTAS, EstatusTransaccionPago.RECHAZADA,
                codigo, recortar(texto), LocalDateTime.now());
        if (cambiadas == 0) {
            return false;
        }
        cuentaRepository.reintegrarSaldo(cuentaId, monto);
        return true;
    }

    /** El monto permanece retenido: ni se reintegra ni se reintenta hasta conocer el resultado real. */
    @Transactional
    public boolean marcarIndeterminada(Long transaccionId, String codigo, String texto) {
        return transaccionPagoRepository.cambiarEstatus(transaccionId, EnumSet.of(EstatusTransaccionPago.PENDIENTE),
                EstatusTransaccionPago.INDETERMINADA, codigo, recortar(texto), LocalDateTime.now()) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<TransaccionPagoEntity> buscarPorLlave(String idempotencyKey) {
        return transaccionPagoRepository.findConCuentaByIdempotencyKey(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Optional<TransaccionPagoEntity> buscarPorUpc(String upc) {
        return transaccionPagoRepository.findConCuentaByUpc(upc);
    }

    @Transactional(readOnly = true)
    public boolean existeUpc(String upc) {
        return transaccionPagoRepository.existsByUpc(upc);
    }

    @Transactional(readOnly = true)
    public List<TransaccionPagoEntity> buscarAnteriores(EstatusTransaccionPago estatus, LocalDateTime limite) {
        return transaccionPagoRepository.findByEstatusAndFechaTransaccionBefore(estatus, limite);
    }

    @Transactional(readOnly = true)
    public BigDecimal saldo(Long cuentaId) {
        return cuentaRepository.findSaldoById(cuentaId).orElse(null);
    }

    private static String recortar(String texto) {
        return texto != null && texto.length() > MAX_TEXTO ? texto.substring(0, MAX_TEXTO) : texto;
    }
}
