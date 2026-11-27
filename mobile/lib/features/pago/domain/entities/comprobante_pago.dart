/// Estatus de una operación de pago. Un valor desconocido se trata como
/// pendiente (nunca como éxito).
enum EstatusPago {
  confirmada,
  rechazada,
  indeterminada,
  pendiente,
  desconocido;

  static EstatusPago desdeTexto(String? texto) {
    switch ((texto ?? '').trim().toUpperCase()) {
      case 'CONFIRMADA':
        return EstatusPago.confirmada;
      case 'RECHAZADA':
        return EstatusPago.rechazada;
      case 'INDETERMINADA':
        return EstatusPago.indeterminada;
      case 'PENDIENTE':
        return EstatusPago.pendiente;
      default:
        return EstatusPago.desconocido;
    }
  }
}

/// Motivo (enumerado) que acompaña a un rechazo o a un error de negocio.
enum MotivoPago {
  saldoInsuficienteCuenta,
  montoNoPermitido,
  productoNoPagable,
  idempotencyKeyInvalida,
  pinIncorrecto,
  sinPin,
  cuentaInactiva,
  noTitular,
  clienteInactivo,
  cuentaNoEncontrada,
  productoNoEncontrado,
  llaveEnUso,
  llaveOtrosDatos,
  pinBloqueado,
  validacionRequerida,
  referenciaInvalida,
  servicioNoDisponible,
  rechazadoProveedor,
  errorGestoPago,
  proveedorNoDisponible,
  operacionNoEncontrada,
  desconocido;

  static MotivoPago desdeTexto(String? texto) {
    switch ((texto ?? '').trim().toUpperCase()) {
      case 'SALDO_INSUFICIENTE_CUENTA':
        return MotivoPago.saldoInsuficienteCuenta;
      case 'MONTO_NO_PERMITIDO':
        return MotivoPago.montoNoPermitido;
      case 'PRODUCTO_NO_PAGABLE':
        return MotivoPago.productoNoPagable;
      case 'IDEMPOTENCY_KEY_INVALIDA':
        return MotivoPago.idempotencyKeyInvalida;
      case 'PIN_INCORRECTO':
        return MotivoPago.pinIncorrecto;
      case 'SIN_PIN':
        return MotivoPago.sinPin;
      case 'CUENTA_INACTIVA':
        return MotivoPago.cuentaInactiva;
      case 'NO_TITULAR':
        return MotivoPago.noTitular;
      case 'CLIENTE_INACTIVO':
        return MotivoPago.clienteInactivo;
      case 'CUENTA_NO_ENCONTRADA':
        return MotivoPago.cuentaNoEncontrada;
      case 'PRODUCTO_NO_ENCONTRADO':
        return MotivoPago.productoNoEncontrado;
      case 'LLAVE_EN_USO':
        return MotivoPago.llaveEnUso;
      case 'LLAVE_OTROS_DATOS':
        return MotivoPago.llaveOtrosDatos;
      case 'PIN_BLOQUEADO':
        return MotivoPago.pinBloqueado;
      case 'VALIDACION_REQUERIDA':
        return MotivoPago.validacionRequerida;
      case 'REFERENCIA_INVALIDA':
        return MotivoPago.referenciaInvalida;
      case 'SERVICIO_NO_DISPONIBLE':
        return MotivoPago.servicioNoDisponible;
      case 'RECHAZADO_PROVEEDOR':
        return MotivoPago.rechazadoProveedor;
      case 'ERROR_GESTOPAGO':
        return MotivoPago.errorGestoPago;
      case 'PROVEEDOR_NO_DISPONIBLE':
        return MotivoPago.proveedorNoDisponible;
      case 'OPERACION_NO_ENCONTRADA':
        return MotivoPago.operacionNoEncontrada;
      default:
        return MotivoPago.desconocido;
    }
  }
}

/// `PagoServicioResponse` del backend. Todos los campos pueden venir nulos.
///
/// `mensajeRespuesta` y `codigoRespuesta` no se modelan a propósito: la
/// pantalla jamás los muestra.
class ComprobantePago {
  const ComprobantePago({
    required this.estatus,
    this.numCuenta,
    this.upc,
    this.motivo,
    this.monto,
    this.saldoRestante,
    this.folio,
    this.idTx,
    this.identificadorOperacion,
    this.comision,
    this.pinServicio,
    this.leyenda,
    this.servicio,
    this.producto,
    this.referenciaEnmascarada,
    this.telefonoEnmascarado,
    this.fechaTransaccion,
    this.idServicio,
    this.idProducto,
    this.logoUrl,
  });

  final EstatusPago estatus;

  /// Enmascarada por el backend.
  final String? numCuenta;
  final String? upc;
  final MotivoPago? motivo;
  final double? monto;
  final double? saldoRestante;
  final String? folio;
  final String? idTx;
  final String? identificadorOperacion;
  final double? comision;

  /// Código de tarjeta de regalo: solo se muestra en el comprobante exitoso.
  final String? pinServicio;

  /// Texto informativo del proveedor: solo en el comprobante exitoso.
  final String? leyenda;
  final String? servicio;
  final String? producto;
  final String? referenciaEnmascarada;
  final String? telefonoEnmascarado;
  final String? fechaTransaccion;
  final int? idServicio;
  final int? idProducto;
  final String? logoUrl;

  bool get tieneFolio => (folio ?? '').trim().isNotEmpty;
}
