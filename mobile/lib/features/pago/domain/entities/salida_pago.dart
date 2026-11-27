import 'comprobante_pago.dart';

/// Lo que el flujo de pago entiende de una respuesta (máquina de estados).
sealed class SalidaPago {
  const SalidaPago();
}

final class PagoConfirmado extends SalidaPago {
  const PagoConfirmado(this.comprobante);
  final ComprobantePago comprobante;
}

/// Sin confirmación: 202, estatus indeterminado, tiempo agotado o sin red.
final class PagoPendiente extends SalidaPago {
  const PagoPendiente({this.comprobante, this.porRed = false});
  final ComprobantePago? comprobante;
  final bool porRed;
}

final class PagoRechazado extends SalidaPago {
  const PagoRechazado(this.motivo, {this.comprobante});
  final MotivoPago motivo;
  final ComprobantePago? comprobante;
}

final class PagoPinIncorrecto extends SalidaPago {
  const PagoPinIncorrecto({this.intentosRestantes});
  final int? intentosRestantes;
}

final class PagoPinBloqueado extends SalidaPago {
  const PagoPinBloqueado();
}

final class PagoValidacionExpirada extends SalidaPago {
  const PagoValidacionExpirada();
}

final class PagoSaldoInsuficiente extends SalidaPago {
  const PagoSaldoInsuficiente();
}

/// 401: el interceptor ya cierra la sesión.
final class PagoSesionInvalida extends SalidaPago {
  const PagoSesionInvalida();
}

final class PagoErrorGenerico extends SalidaPago {
  const PagoErrorGenerico();
}

/// Resultado de una consulta de estado.
sealed class SalidaConsulta {
  const SalidaConsulta();
}

final class ConsultaConfirmada extends SalidaConsulta {
  const ConsultaConfirmada(this.comprobante);
  final ComprobantePago comprobante;
}

final class ConsultaRechazada extends SalidaConsulta {
  const ConsultaRechazada(this.motivo, this.comprobante);
  final MotivoPago motivo;
  final ComprobantePago? comprobante;
}

final class ConsultaPendiente extends SalidaConsulta {
  const ConsultaPendiente(this.comprobante);
  final ComprobantePago? comprobante;
}

final class ConsultaNoEncontrada extends SalidaConsulta {
  const ConsultaNoEncontrada();
}

final class ConsultaSesionInvalida extends SalidaConsulta {
  const ConsultaSesionInvalida();
}

/// Cualquier otro fallo: se sigue sondeando.
final class ConsultaFallida extends SalidaConsulta {
  const ConsultaFallida();
}
