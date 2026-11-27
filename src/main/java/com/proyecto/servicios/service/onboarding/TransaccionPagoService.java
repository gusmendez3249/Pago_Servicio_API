package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.enums.OrigenConfirmacion;
import com.proyecto.servicios.exception.IntegrationException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.gestopago.DatosProveedor;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.repositorys.onboarding.TransaccionPagoRepository;
import com.proyecto.servicios.service.CifradoService;
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
 * Toda transición de estatus es condicionada (UPDATE ... where estatus in ABIERTAS): sin doble confirmación ni doble reintegro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionPagoService {

    private static final Set<EstatusTransaccionPago> ABIERTAS =
            EnumSet.of(EstatusTransaccionPago.PENDIENTE, EstatusTransaccionPago.INDETERMINADA);
    private static final int MAX_TEXTO = 500;
    private static final int MAX_FOLIO = 30;
    private static final int MAX_FECHA_PROVEEDOR = 30;

    private final CuentaRepository cuentaRepository;
    private final TransaccionPagoRepository transaccionPagoRepository;
    private final CifradoService cifradoService;

    /**
     * Descuenta el saldo con un UPDATE condicionado (saldo >= monto y cuenta ACTIVA) y registra la operación
     * PENDIENTE en la misma transacción. Si la llave de idempotencia ya existe, el índice único revierte ambas cosas.
     */
    @Transactional
    public TransaccionPagoEntity reservar(Long cuentaId, TransaccionPagoEntity nueva) {
        if (cuentaRepository.descontarSaldo(cuentaId, nueva.getMonto()) == 0) {
            throw new ReglaNegocioException("Saldo insuficiente para cubrir el monto del pago o la cuenta no está activa.",
                    MotivoPago.SALDO_INSUFICIENTE_CUENTA);
        }
        nueva.setCuenta(cuentaRepository.getReferenceById(cuentaId));
        nueva.setEstatus(EstatusTransaccionPago.PENDIENTE);
        return transaccionPagoRepository.saveAndFlush(nueva);
    }

    /** Confirma sin datos del proveedor (conciliación manual). @return false si la operación ya estaba resuelta */
    @Transactional
    public boolean confirmar(Long transaccionId, String codigo, String texto) {
        return transaccionPagoRepository.confirmarSinDatos(transaccionId, ABIERTAS, EstatusTransaccionPago.CONFIRMADA,
                codigo, recortar(texto, MAX_TEXTO), LocalDateTime.now(), OrigenConfirmacion.GESTOPAGO) == 1;
    }

    /**
     * Confirma guardando el folio (NUM_AUTORIZACION), el id de transacción, la comisión, la leyenda y el PIN de la
     * tarjeta de regalo CIFRADO. Misma condición de estatus abierto: @return false si la operación ya estaba resuelta
     */
    @Transactional
    public boolean confirmar(Long transaccionId, String codigo, String texto, DatosProveedor datos) {
        if (datos == null) {
            return confirmar(transaccionId, codigo, texto);
        }
        return transaccionPagoRepository.confirmarConDatos(transaccionId, ABIERTAS, EstatusTransaccionPago.CONFIRMADA,
                codigo, recortar(texto, MAX_TEXTO), LocalDateTime.now(),
                recortar(datos.numAutorizacion(), MAX_FOLIO), recortar(datos.idTx(), MAX_FOLIO),
                datos.comision(), datos.monto(), recortar(datos.fecha(), MAX_FECHA_PROVEEDOR),
                cifrarPin(datos.pin(), transaccionId), recortar(datos.leyenda(), MAX_TEXTO),
                OrigenConfirmacion.GESTOPAGO) == 1;
    }

    /** Marca la operación como RECHAZADA y devuelve el monto a la cuenta (solo una vez). */
    @Transactional
    public boolean rechazarYReintegrar(Long transaccionId, Long cuentaId, BigDecimal monto, String codigo, String texto) {
        return rechazarYReintegrar(transaccionId, cuentaId, monto, codigo, texto, null);
    }

    /** Igual que la anterior, registrando el motivo enumerado del rechazo. */
    @Transactional
    public boolean rechazarYReintegrar(Long transaccionId, Long cuentaId, BigDecimal monto, String codigo, String texto,
                                       MotivoPago motivo) {
        int cambiadas = transaccionPagoRepository.cambiarEstatusConMotivo(transaccionId, ABIERTAS,
                EstatusTransaccionPago.RECHAZADA, codigo, recortar(texto, MAX_TEXTO), LocalDateTime.now(), motivo);
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
                EstatusTransaccionPago.INDETERMINADA, codigo, recortar(texto, MAX_TEXTO), LocalDateTime.now()) == 1;
    }

    /**
     * Reclama de forma atómica una consulta a confirmTx (incrementa los intentos y fija la fecha de la última).
     * @return false si la operación ya no está INDETERMINADA o se consultó hace menos de {@code limite}
     */
    @Transactional
    public boolean reclamarConsultaConfirmacion(Long transaccionId, LocalDateTime ahora, LocalDateTime limite) {
        return transaccionPagoRepository.reclamarConsulta(transaccionId, ahora, limite) == 1;
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

    /**
     * El PIN de una tarjeta de regalo se guarda cifrado. Si no se pudiera cifrar, la operación se confirma igual
     * (el proveedor ya cobró): se registra el error SIN el valor y el PIN queda sin guardar.
     */
    private String cifrarPin(String pin, Long transaccionId) {
        if (pin == null || pin.isBlank()) {
            return null;
        }
        try {
            return cifradoService.cifrar(pin);
        } catch (IntegrationException exception) {
            log.error("No se pudo cifrar el PIN del servicio; se confirma sin guardarlo. transaccionId={} causa={}",
                    transaccionId, exception.getErrorCode());
            return null;
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto != null && texto.length() > maximo ? texto.substring(0, maximo) : texto;
    }
}
