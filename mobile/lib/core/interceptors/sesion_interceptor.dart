import 'dart:convert';

import 'package:dio/dio.dart';

import '../http/api_client.dart';

/// Reacciona a las respuestas de peticiones autenticadas:
/// - 2xx: registra actividad (renueva la ventana de inactividad).
/// - 401: invalida la sesión local. Sin reintentos.
/// - 403 CLIENTE_INACTIVO / CUENTA_INACTIVA: cierra la sesión local.
class SesionInterceptor extends Interceptor {
  SesionInterceptor({
    required this.alActividad,
    required this.alNoAutorizado,
    this.alCuentaInactiva,
  });

  final void Function() alActividad;
  final void Function() alNoAutorizado;

  /// 403 con motivo CLIENTE_INACTIVO o CUENTA_INACTIVA.
  final void Function()? alCuentaInactiva;

  bool _esAutenticada(RequestOptions o) =>
      o.extra[kExtraAutenticada] != false &&
      o.headers.containsKey('Authorization');

  @override
  void onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) {
    final status = response.statusCode ?? 0;
    if (status >= 200 &&
        status < 300 &&
        _esAutenticada(response.requestOptions)) {
      alActividad();
    }
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (err.response?.statusCode == 401 && _esAutenticada(err.requestOptions)) {
      alNoAutorizado();
    } else if (err.response?.statusCode == 403 &&
        _esAutenticada(err.requestOptions) &&
        _esCuentaInactiva(err.response?.data)) {
      alCuentaInactiva?.call();
    }
    handler.next(err);
  }

  static bool _esCuentaInactiva(Object? cuerpo) {
    var datos = cuerpo;
    if (datos is String) {
      try {
        datos = jsonDecode(datos);
      } catch (_) {
        return false;
      }
    }
    if (datos is! Map) return false;
    final detalle = datos['data'];
    if (detalle is! Map) return false;
    final motivo = detalle['motivo'];
    return motivo == 'CLIENTE_INACTIVO' || motivo == 'CUENTA_INACTIVA';
  }
}
