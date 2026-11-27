package com.proyecto.servicios.enums;

/**
 * Motivo enumerado de un error de negocio o de un pago rechazado. Viaja en {@code data.motivo} de la respuesta para que
 * los clientes muestren su propio mensaje: nunca se devuelve texto del proveedor.
 */
public enum MotivoPago {

    // validaciones previas al cobro (400)
    SALDO_INSUFICIENTE_CUENTA,
    MONTO_NO_PERMITIDO,
    PRODUCTO_NO_PAGABLE,
    IDEMPOTENCY_KEY_INVALIDA,

    // titularidad, cuenta y PIN (403 / 423)
    NO_TITULAR,
    CLIENTE_INACTIVO,
    CUENTA_INACTIVA,
    PIN_INCORRECTO,
    SIN_PIN,
    PIN_BLOQUEADO,

    // recursos inexistentes (404)
    CUENTA_NO_ENCONTRADA,
    PRODUCTO_NO_ENCONTRADO,
    OPERACION_NO_ENCONTRADA,

    // idempotencia (409)
    LLAVE_EN_USO,
    LLAVE_OTROS_DATOS,

    // tope diario de recargas del panel de administrador (422)
    TOPE_DIARIO_CUENTA,
    TOPE_DIARIO_EJECUTIVO,

    // validación de referencia (428)
    VALIDACION_REQUERIDA,

    // pago rechazado (422) o no procesado (5xx)
    REFERENCIA_INVALIDA,
    SERVICIO_NO_DISPONIBLE,
    RECHAZADO_PROVEEDOR,
    /** Gesto Pago respondió con un error propio (p. ej. 05 o 55 "Origen Valido"): la falla es del proveedor, no del usuario. */
    ERROR_GESTOPAGO,
    PROVEEDOR_NO_DISPONIBLE
}
