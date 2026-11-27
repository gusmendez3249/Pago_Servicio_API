import '../../domain/entities/comprobante_pago.dart';
import '../../domain/entities/cuenta_cliente.dart';
import '../../domain/entities/producto_pago.dart';
import '../../domain/utils/reglas_pago.dart';

enum FasePago {
  preparando,
  capturandoDatos,
  resumen,
  confirmando,
  procesando,
  exitoso,
  rechazado,
  pendiente,
  error,
}

/// Causa de la fase `error`.
enum ErrorPago { sinCuenta, pinBloqueado, validacionExpirada, generico }

/// Qué se captura en `capturandoDatos`.
enum CapturaPago { telefono, importe }

const Object _sin = Object();

/// Estado del flujo de pago. Vive solo en memoria. NUNCA contiene el PIN.
///
/// Los datos sensibles ([cuentas] con el número completo, [telefono],
/// [referencia], [idValidacion], [importe], [llave]) se vacían al terminar.
class PagoState {
  const PagoState({
    this.fase = FasePago.preparando,
    this.producto,
    this.esReferencia = false,
    this.captura,
    this.cuentas = const [],
    this.cuentaIndex = 0,
    this.referencia = '',
    this.idValidacion = '',
    this.telefono = '',
    this.importe,
    this.total,
    this.llave,
    this.enVuelo = false,
    this.avisoPin = false,
    this.intentosRestantes,
    this.error,
    this.comprobante,
    this.motivoRechazo,
    this.sondeando = false,
    this.consultando = false,
    this.pagoNoLlego = false,
    this.sinConexion = false,
    this.reintentoSinLlegar = false,
    this.saldoRechazado = false,
    this.errorTelefono,
    this.errorImporte,
    this.totalMostrado,
  });

  final FasePago fase;
  final ProductoPago? producto;
  final bool esReferencia;
  final CapturaPago? captura;
  final List<CuentaCliente> cuentas;
  final int cuentaIndex;
  final String referencia;
  final String idValidacion;
  final String telefono;
  final double? importe;
  final double? total;
  final String? llave;
  final bool enVuelo;

  /// En `confirmando`: el intento anterior fue con un PIN incorrecto.
  final bool avisoPin;
  final int? intentosRestantes;
  final ErrorPago? error;
  final ComprobantePago? comprobante;
  final MotivoPago? motivoRechazo;
  final bool sondeando;
  final bool consultando;
  final bool pagoNoLlego;
  final bool sinConexion;

  /// `confirmando` abierto desde "Reintentar pago" (misma llave).
  final bool reintentoSinLlegar;

  /// El backend respondió saldo insuficiente: "Pagar" queda deshabilitado.
  final bool saldoRechazado;
  final ErrorTelefono? errorTelefono;
  final ErrorImporte? errorImporte;

  /// Total que se muestra en el mensaje de éxito (sobrevive a la limpieza).
  final double? totalMostrado;

  CuentaCliente? get cuenta =>
      (cuentaIndex >= 0 && cuentaIndex < cuentas.length)
          ? cuentas[cuentaIndex]
          : null;

  ResumenPago? get resumen {
    final c = cuenta;
    final t = total;
    if (c == null || t == null) return null;
    return calcularResumen(total: t, saldo: c.saldo);
  }

  bool get saldoSuficiente =>
      !saldoRechazado && (resumen?.saldoSuficiente ?? false);

  String? get upc => comprobante?.upc;

  PagoState copiar({
    FasePago? fase,
    ProductoPago? producto,
    bool? esReferencia,
    Object? captura = _sin,
    List<CuentaCliente>? cuentas,
    int? cuentaIndex,
    String? referencia,
    String? idValidacion,
    String? telefono,
    Object? importe = _sin,
    Object? total = _sin,
    Object? llave = _sin,
    bool? enVuelo,
    bool? avisoPin,
    Object? intentosRestantes = _sin,
    Object? error = _sin,
    Object? comprobante = _sin,
    Object? motivoRechazo = _sin,
    bool? sondeando,
    bool? consultando,
    bool? pagoNoLlego,
    bool? sinConexion,
    bool? reintentoSinLlegar,
    bool? saldoRechazado,
    Object? errorTelefono = _sin,
    Object? errorImporte = _sin,
    Object? totalMostrado = _sin,
  }) {
    return PagoState(
      fase: fase ?? this.fase,
      producto: producto ?? this.producto,
      esReferencia: esReferencia ?? this.esReferencia,
      captura:
          identical(captura, _sin) ? this.captura : captura as CapturaPago?,
      cuentas: cuentas ?? this.cuentas,
      cuentaIndex: cuentaIndex ?? this.cuentaIndex,
      referencia: referencia ?? this.referencia,
      idValidacion: idValidacion ?? this.idValidacion,
      telefono: telefono ?? this.telefono,
      importe: identical(importe, _sin) ? this.importe : importe as double?,
      total: identical(total, _sin) ? this.total : total as double?,
      llave: identical(llave, _sin) ? this.llave : llave as String?,
      enVuelo: enVuelo ?? this.enVuelo,
      avisoPin: avisoPin ?? this.avisoPin,
      intentosRestantes: identical(intentosRestantes, _sin)
          ? this.intentosRestantes
          : intentosRestantes as int?,
      error: identical(error, _sin) ? this.error : error as ErrorPago?,
      comprobante: identical(comprobante, _sin)
          ? this.comprobante
          : comprobante as ComprobantePago?,
      motivoRechazo: identical(motivoRechazo, _sin)
          ? this.motivoRechazo
          : motivoRechazo as MotivoPago?,
      sondeando: sondeando ?? this.sondeando,
      consultando: consultando ?? this.consultando,
      pagoNoLlego: pagoNoLlego ?? this.pagoNoLlego,
      sinConexion: sinConexion ?? this.sinConexion,
      reintentoSinLlegar: reintentoSinLlegar ?? this.reintentoSinLlegar,
      saldoRechazado: saldoRechazado ?? this.saldoRechazado,
      errorTelefono: identical(errorTelefono, _sin)
          ? this.errorTelefono
          : errorTelefono as ErrorTelefono?,
      errorImporte: identical(errorImporte, _sin)
          ? this.errorImporte
          : errorImporte as ErrorImporte?,
      totalMostrado: identical(totalMostrado, _sin)
          ? this.totalMostrado
          : totalMostrado as double?,
    );
  }

  /// Igual que este estado pero sin ningún dato sensible: se conserva solo lo
  /// que la pantalla de resultado necesita mostrar.
  PagoState sinDatosSensibles({
    required FasePago fase,
    ErrorPago? error,
    ComprobantePago? comprobante,
    MotivoPago? motivoRechazo,
    double? totalMostrado,
  }) {
    return PagoState(
      fase: fase,
      producto: producto,
      esReferencia: esReferencia,
      error: error,
      comprobante: comprobante,
      motivoRechazo: motivoRechazo,
      totalMostrado: totalMostrado,
    );
  }
}
