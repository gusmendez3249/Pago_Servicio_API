import '../../catalogo/domain/utils/formato_catalogo.dart';
import '../domain/entities/validacion_referencia.dart';
import '../domain/utils/formato_referencia.dart';

/// Textos EXACTOS de la especificación. La pantalla muestra solo estos:
/// nunca texto del backend ni de la pasarela.
abstract final class TextosReferencia {
  static const String sinAdeudo =
      'Referencia verificada. No tienes adeudo pendiente.';
  static const String soloConsulta =
      'Este servicio solo permite consultar el saldo.';
  static const String invalida =
      'No encontramos esa referencia. Revísala e inténtalo de nuevo.';
  static const String formato =
      'La referencia solo admite letras y números, sin espacios.';
  static const String vacia = 'Ingresa tu referencia.';
  static const String error =
      'No pudimos verificar tu referencia en este momento. Inténtalo más tarde.';
  static const String expirada =
      'La verificación expiró. Valida tu referencia de nuevo para continuar.';
  static const String ayuda = '¿Dónde encuentro mi referencia?';
  static const String pagoNoDisponibleProducto =
      'El pago de este producto estará disponible próximamente.';

  static const String soloFormato =
      'No podemos consultar el adeudo de este servicio. Revisa que tu '
      'referencia sea correcta antes de continuar.';
  static const String nadaPorPagar = 'No hay nada por pagar.';
  static const String montoSiguientePaso =
      'El monto se captura en el siguiente paso';

  static String totalAPagar(double monto) =>
      'Total a pagar: ${formatearMoneda(monto.abs())}';

  static String adeudo(double saldo) =>
      'Referencia verificada. Tienes un adeudo de ${formatearMoneda(saldo.abs())}.';

  static String saldoAFavor(double saldo) =>
      'Referencia verificada. Tienes un saldo a favor de ${formatearMoneda(saldo.abs())}.';

  static String errorFormato(ErrorFormatoReferencia error) =>
      error == ErrorFormatoReferencia.vacia ? vacia : formato;

  /// Texto de la situación (sin el prefijo "Referencia verificada").
  static String situacion(SituacionSaldo situacion, double? saldo) {
    final monto = formatearMoneda((saldo ?? 0).abs());
    switch (situacion) {
      case SituacionSaldo.adeudo:
        return 'Adeudo: $monto';
      case SituacionSaldo.sinAdeudo:
        return 'Sin adeudo pendiente';
      case SituacionSaldo.saldoAFavor:
        return 'Saldo a favor: $monto';
      case SituacionSaldo.desconocida:
        return '';
    }
  }

  /// Texto principal de una referencia válida con pago permitido.
  static String validada(SituacionSaldo situacion, double saldo) {
    switch (situacion) {
      case SituacionSaldo.adeudo:
        return adeudo(saldo);
      case SituacionSaldo.saldoAFavor:
        return saldoAFavor(saldo);
      case SituacionSaldo.sinAdeudo:
      case SituacionSaldo.desconocida:
        return sinAdeudo;
    }
  }
}
