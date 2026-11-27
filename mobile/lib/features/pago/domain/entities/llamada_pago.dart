import '../../../../core/http/result.dart';
import 'comprobante_pago.dart';

/// Resultado de una llamada de pago o de consulta de estado, ya traducido a
/// entidades de dominio (sin JSON ni excepciones).
sealed class LlamadaPago {
  const LlamadaPago();
}

/// Respuesta 2xx con (o sin) comprobante legible.
final class LlamadaOk extends LlamadaPago {
  const LlamadaOk({required this.httpStatus, this.comprobante});

  final int httpStatus;
  final ComprobantePago? comprobante;
}

/// Respuesta de error o fallo de red.
final class LlamadaFallo extends LlamadaPago {
  const LlamadaFallo({
    required this.tipo,
    this.httpStatus,
    this.motivo,
    this.intentosRestantes,
    this.minutosBloqueo,
    this.comprobante,
  });

  final TipoFallo tipo;
  final int? httpStatus;
  final MotivoPago? motivo;
  final int? intentosRestantes;
  final int? minutosBloqueo;

  /// Presente en un 422 (pago rechazado).
  final ComprobantePago? comprobante;

  bool get esExitoHttp {
    final s = httpStatus;
    return s != null && s >= 200 && s < 300;
  }
}
