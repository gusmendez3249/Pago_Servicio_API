package com.proyecto.servicios.mapper;

import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.enums.OrigenConfirmacion;
import com.proyecto.servicios.service.CatalogoPresentacionService;
import com.proyecto.servicios.service.CifradoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Único lugar donde se arma un {@link PagoServicioResponse} a partir de una operación guardada: lo usan el pago, la
 * repetición idempotente, la consulta de estado y la conciliación. Los mensajes son texto fijo del backend: el
 * TEXTO que envía el proveedor nunca se devuelve. El PIN de tarjeta de regalo se descifra solo si la operación está
 * CONFIRMADA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagoServicioMapper {

    public static final String MENSAJE_CONFIRMADA = "Pago realizado correctamente.";
    public static final String MENSAJE_INDETERMINADA = "El pago quedó en verificación con el proveedor. El monto permanece "
            + "retenido hasta confirmar el resultado; no repita el pago con otra Idempotency-Key. Para consultar el "
            + "estado use POST /api/v1/pagos/estado con el upc o la misma Idempotency-Key.";
    public static final String MENSAJE_RECHAZADA = "El pago no se realizó. El saldo fue reintegrado a la cuenta.";

    private final CifradoService cifradoService;
    private final CatalogoPresentacionService presentacion;

    /**
     * @param numeroCuenta número completo de la cuenta de cargo (se devuelve enmascarado)
     * @param saldo        saldo actual de la cuenta (el monto de un pago INDETERMINADO sigue retenido)
     */
    public PagoServicioResponse aRespuesta(TransaccionPagoEntity tx, String numeroCuenta, BigDecimal saldo) {
        return construir(tx, numeroCuenta, saldo, true);
    }

    /**
     * Elemento de la lista del historial: igual que {@link #aRespuesta} pero SIN saldo y SIN PIN (ni siquiera se
     * descifra). Las filas antiguas sin nombres guardados toman el del catálogo si se conoce.
     */
    public PagoServicioResponse aElementoHistorial(TransaccionPagoEntity tx, String numeroCuenta,
                                                   String servicioCatalogo, String productoCatalogo) {
        PagoServicioResponse elemento = construir(tx, numeroCuenta, null, false);
        if (elemento.getServicio() == null) {
            elemento.setServicio(servicioCatalogo);
        }
        if (elemento.getProducto() == null) {
            elemento.setProducto(productoCatalogo);
        }
        return elemento;
    }

    private PagoServicioResponse construir(TransaccionPagoEntity tx, String numeroCuenta, BigDecimal saldo, boolean conPin) {
        EstatusTransaccionPago estatus = tx.getEstatus();
        boolean confirmada = estatus == EstatusTransaccionPago.CONFIRMADA;
        MotivoPago motivo = estatus == EstatusTransaccionPago.RECHAZADA
                ? (tx.getMotivo() != null ? tx.getMotivo() : MotivoPago.RECHAZADO_PROVEEDOR)
                : null;
        return PagoServicioResponse.builder()
                .numCuenta(enmascarar(numeroCuenta))
                .upc(tx.getUpc())
                .estatus(estatus)
                .motivo(motivo)
                .origenConfirmacion(confirmada ? tx.getOrigenConfirmacion() : null)
                .simulado(confirmada && tx.getOrigenConfirmacion() == OrigenConfirmacion.SIMULADO)
                .monto(tx.getMonto())
                .saldoRestante(saldo)
                .folio(tx.getFolio())
                .idTx(tx.getIdTx())
                .identificadorOperacion(identificador(tx))
                .comision(tx.getComision())
                .pinServicio(conPin && confirmada ? descifrarPin(tx) : null)
                .leyenda(confirmada ? tx.getLeyenda() : null)
                .servicio(tx.getNombreServicio())
                .producto(tx.getNombreProducto())
                .referenciaEnmascarada(tx.getReferencia() == null ? null : enmascarar(tx.getReferencia()))
                .telefonoEnmascarado(tx.getTelefono() == null ? null : enmascarar(tx.getTelefono()))
                .codigoRespuesta(tx.getCodigoRespuesta())
                .mensajeRespuesta(mensaje(estatus))
                .idServicio(tx.getIdServicio())
                .idProducto(tx.getIdProducto())
                .logoUrl(presentacion.logoUrl(tx.getIdServicio()))
                .fechaTransaccion(tx.getFechaTransaccion())
                .build();
    }

    /** Folio; si no hay, id de transacción; si no hay, el upc (que siempre existe). */
    static String identificador(TransaccionPagoEntity tx) {
        if (tx.getFolio() != null && !tx.getFolio().isBlank()) {
            return tx.getFolio();
        }
        if (tx.getIdTx() != null && !tx.getIdTx().isBlank()) {
            return tx.getIdTx();
        }
        return tx.getUpc();
    }

    static String mensaje(EstatusTransaccionPago estatus) {
        if (estatus == null) {
            return null;
        }
        return switch (estatus) {
            case CONFIRMADA -> MENSAJE_CONFIRMADA;
            case RECHAZADA -> MENSAJE_RECHAZADA;
            case INDETERMINADA, PENDIENTE -> MENSAJE_INDETERMINADA;
        };
    }

    /** Deja visibles solo los últimos 4 caracteres (cuenta, teléfono o referencia). */
    public static String enmascarar(String valor) {
        if (valor == null || valor.length() <= 4) {
            return valor;
        }
        return "*".repeat(valor.length() - 4) + valor.substring(valor.length() - 4);
    }

    private String descifrarPin(TransaccionPagoEntity tx) {
        if (tx.getPinServicio() == null || tx.getPinServicio().isBlank()) {
            return null;
        }
        try {
            return cifradoService.descifrar(tx.getPinServicio());
        } catch (RuntimeException exception) {
            // Sin el valor ni la causa detallada: solo el tipo
            log.error("No se pudo descifrar el PIN del servicio. upc={} causa={}", tx.getUpc(),
                    exception.getClass().getSimpleName());
            return null;
        }
    }
}
