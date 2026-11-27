package com.proyecto.servicios.enums;

/**
 * Quién dio por aplicado un pago CONFIRMADO.
 * <ul>
 *   <li>{@link #GESTOPAGO}: el proveedor lo aplicó (CODIGO 01 de sendTx, 06 de confirmTx o conciliación de un ejecutivo).</li>
 *   <li>{@link #SIMULADO}: solo registros históricos. Hubo un modo de prueba (ya retirado) que daba por exitoso un pago
 *       que GestoPago no aplicó; esas filas conservan este valor para que no se confundan con pagos reales.</li>
 * </ul>
 */
public enum OrigenConfirmacion {
    GESTOPAGO,
    SIMULADO
}
