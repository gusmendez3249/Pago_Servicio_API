/// Resultado de la verificación de una referencia ("estado" del contrato).
enum EstadoValidacion {
  valida,
  invalida,

  /// Valor que esta versión no conoce: se trata como error.
  desconocido;

  static EstadoValidacion desdeTexto(String? texto) {
    switch ((texto ?? '').trim().toUpperCase()) {
      case 'VALIDA':
        return EstadoValidacion.valida;
      case 'INVALIDA':
        return EstadoValidacion.invalida;
      default:
        return EstadoValidacion.desconocido;
    }
  }
}

/// Motivo cuando la referencia no es válida.
enum MotivoValidacion {
  referenciaInvalida,
  desconocido;

  static MotivoValidacion desdeTexto(String? texto) {
    switch ((texto ?? '').trim().toUpperCase()) {
      case 'REFERENCIA_INVALIDA':
        return MotivoValidacion.referenciaInvalida;
      default:
        return MotivoValidacion.desconocido;
    }
  }
}

/// Situación del saldo de una referencia válida.
enum SituacionSaldo {
  adeudo,
  sinAdeudo,
  saldoAFavor,
  desconocida;

  static SituacionSaldo desdeTexto(String? texto) {
    switch ((texto ?? '').trim().toUpperCase()) {
      case 'ADEUDO':
        return SituacionSaldo.adeudo;
      case 'SIN_ADEUDO':
        return SituacionSaldo.sinAdeudo;
      case 'SALDO_A_FAVOR':
        return SituacionSaldo.saldoAFavor;
      default:
        return SituacionSaldo.desconocida;
    }
  }

  /// Deduce la situación del signo del saldo (>0 adeudo, 0 sin adeudo,
  /// <0 saldo a favor).
  static SituacionSaldo desdeSaldo(double saldo) {
    if (saldo > 0) return SituacionSaldo.adeudo;
    if (saldo < 0) return SituacionSaldo.saldoAFavor;
    return SituacionSaldo.sinAdeudo;
  }
}

/// Cómo se verificó la referencia. Null o desconocido se trata como en línea.
enum VerificacionReferencia {
  enLinea,
  soloFormato;

  static VerificacionReferencia desdeTexto(String? texto) {
    switch ((texto ?? '').trim().toUpperCase()) {
      case 'SOLO_FORMATO':
        return VerificacionReferencia.soloFormato;
      default:
        return VerificacionReferencia.enLinea;
    }
  }
}

/// Respuesta de validar una referencia. Los textos que trae (nombres) son
/// datos para el resumen; nunca se muestran mensajes del backend.
class ValidacionReferencia {
  const ValidacionReferencia({
    required this.estado,
    this.motivo,
    this.situacion,
    this.saldo,
    this.idValidacion,
    this.vigenciaSegundos,
    this.puedePagar = false,
    this.idSer,
    this.idProd,
    this.referencia,
    this.servicio,
    this.producto,
    this.verificacion = VerificacionReferencia.enLinea,
    this.montoAPagar,
  });

  final VerificacionReferencia verificacion;
  final double? montoAPagar;
  final EstadoValidacion estado;
  final MotivoValidacion? motivo;
  final SituacionSaldo? situacion;
  final double? saldo;
  final String? idValidacion;
  final int? vigenciaSegundos;
  final bool puedePagar;
  final int? idSer;
  final int? idProd;
  final String? referencia;
  final String? servicio;
  final String? producto;

  /// Situación informada o, si falta, la deducida del saldo; null si no hay
  /// forma de saberla.
  SituacionSaldo? get situacionEfectiva {
    final s = situacion;
    if (s != null && s != SituacionSaldo.desconocida) return s;
    final monto = saldo;
    return monto == null ? null : SituacionSaldo.desdeSaldo(monto);
  }
}

/// Datos que el Sprint 3 necesita para pagar. SOLO en memoria: nunca se
/// guardan en disco ni en almacenamiento seguro.
class DatosPagoReferencia {
  const DatosPagoReferencia({
    required this.idSer,
    required this.idProd,
    required this.referencia,
    required this.idValidacion,
    required this.saldo,
    required this.situacion,
    required this.servicio,
    required this.producto,
    required this.expiraEn,
    this.verificacion = VerificacionReferencia.enLinea,
    this.montoAPagar,
  });

  /// Null cuando solo se validó el formato (no hay consulta de adeudo).
  final VerificacionReferencia verificacion;

  /// Total a pagar si se conoce el adeudo; null si el monto se captura después.
  final double? montoAPagar;

  final int idSer;
  final int idProd;
  final String referencia;
  final String idValidacion;
  final double? saldo;
  final SituacionSaldo? situacion;
  final String servicio;
  final String producto;
  final DateTime expiraEn;
}
