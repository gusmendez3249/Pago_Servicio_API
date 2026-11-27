package com.proyecto.servicios.enums;

/** De dónde sale el monto de un pago: el servidor siempre lo fija, el usuario nunca elige un total distinto. */
public enum OrigenMonto {

    /** Producto de precio fijo (recargas, tarjetas de regalo): el monto es el precio del catálogo. */
    PRECIO_FIJO,

    /** Pago con referencia verificada en línea: el monto es el adeudo total informado por el proveedor. */
    ADEUDO,

    /** Pago con referencia sin consulta en línea: el usuario capturó el importe de su recibo. */
    CAPTURADO
}
