import '../http/result.dart';

/// Traduce un [Failure] a un mensaje en español para mostrar al usuario.
///
/// Regla del proyecto: la pantalla solo muestra textos amigables escritos aquí,
/// elegidos por el tipo de fallo. Nunca se muestra `Failure.mensaje` ni ningún
/// texto, código o detalle técnico que venga del backend o de una excepción.
String mensajeDeFallo(Failure fallo, {bool esLogin = false}) {
  switch (fallo.tipo) {
    case TipoFallo.validacion:
      return 'Revisa los datos que ingresaste.';
    case TipoFallo.noAutenticado:
      return esLogin
          ? 'Usuario o contraseña incorrectos.'
          : 'Tu sesión ya no es válida. Inicia sesión de nuevo.';
    case TipoFallo.prohibido:
      return esLogin
          ? 'Tu cuenta está inactiva. Comunícate con tu ejecutivo para reactivarla.'
          : 'No tienes permiso para realizar esta acción.';
    case TipoFallo.noEncontrado:
      return 'No se encontró la información solicitada.';
    case TipoFallo.bloqueado:
      return 'Tu cuenta está bloqueada. Comunícate con tu ejecutivo para '
          'desbloquearla.';
    case TipoFallo.demasiadosIntentos:
      final segundos = fallo.retryAfterSegundos;
      return segundos == null
          ? 'Demasiados intentos. Espera un momento e inténtalo de nuevo.'
          : 'Demasiados intentos. Espera $segundos segundos e inténtalo de '
              'nuevo.';
    case TipoFallo.servidor:
      return 'Por ahora no podemos atenderte. Inténtalo de nuevo en unos minutos.';
    case TipoFallo.red:
      return 'No pudimos conectarnos. Revisa tu conexión a internet e inténtalo '
          'de nuevo.';
    case TipoFallo.desconocido:
      return 'Algo salió mal. Inténtalo de nuevo.';
  }
}
