package com.proyecto.servicios.enums;

/**
 * Ciclo de vida de un pago de servicio:
 * PENDIENTE -> CONFIRMADA | RECHAZADA | INDETERMINADA, e INDETERMINADA -> CONFIRMADA | RECHAZADA (conciliación).
 */
public enum EstatusTransaccionPago {

    /** Saldo reservado; el envío a GestoPago está en curso. */
    PENDIENTE,

    /** GestoPago confirmó el pago (CODIGO 01). El saldo queda descontado. */
    CONFIRMADA,

    /** El proveedor rechazó la operación o no llegó a enviarse. El saldo se reintegró. */
    RECHAZADA,

    /** No se pudo determinar si el proveedor aplicó el pago. El saldo queda retenido hasta conciliar. */
    INDETERMINADA
}
