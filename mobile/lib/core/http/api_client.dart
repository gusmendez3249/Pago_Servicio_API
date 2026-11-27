import 'dart:convert';

import 'package:dio/dio.dart';

import '../config/app_config.dart';
import 'json_helpers.dart';
import 'result.dart';

/// Clave en `RequestOptions.extra` que indica si la petición es autenticada.
const String kExtraAutenticada = 'autenticada';

/// Cliente HTTP sobre Dio. SIEMPRE devuelve un [Result]; nunca lanza.
class ApiClient {
  ApiClient(this._dio);

  final Dio _dio;

  Future<Result<T>> get<T>(
    String path, {
    required T Function(Object? data) parser,
    Map<String, dynamic>? query,
    bool autenticada = true,
    Duration? receiveTimeout,
  }) {
    return _enviar<T>(
      () => _dio.get<Object?>(
        path,
        queryParameters: query,
        options: _opciones(autenticada, receiveTimeout, null),
      ),
      parser,
    );
  }

  Future<Result<T>> post<T>(
    String path, {
    required T Function(Object? data) parser,
    Object? body,
    bool autenticada = true,
    Duration? receiveTimeout,
    Map<String, String>? headers,
  }) {
    return _enviar<T>(
      () => _dio.post<Object?>(
        path,
        data: body,
        options: _opciones(autenticada, receiveTimeout, headers),
      ),
      parser,
    );
  }

  Options _opciones(
    bool autenticada,
    Duration? receiveTimeout,
    Map<String, String>? headers,
  ) {
    return Options(
      receiveTimeout: receiveTimeout,
      contentType: Headers.jsonContentType,
      headers: headers,
      extra: {kExtraAutenticada: autenticada},
    );
  }

  Future<Result<T>> _enviar<T>(
    Future<Response<Object?>> Function() peticion,
    T Function(Object? data) parser,
  ) async {
    try {
      final respuesta = await peticion();
      return _desdeExito<T>(respuesta, parser);
    } on DioException catch (e) {
      return _desdeDioException(e);
    } catch (_) {
      return const Failure(
        tipo: TipoFallo.desconocido,
        mensaje: 'Ocurrió un error inesperado.',
      );
    }
  }

  Result<T> _desdeExito<T>(
    Response<Object?> respuesta,
    T Function(Object? data) parser,
  ) {
    final status = respuesta.statusCode ?? 200;
    final cuerpo = _normalizar(respuesta.data);
    try {
      if (cuerpo == null) {
        return Ok<T>(parser(null), httpStatus: status);
      }
      if (cuerpo is! Map) {
        return Failure(
          tipo: TipoFallo.desconocido,
          httpStatus: status,
          mensaje: 'La respuesta del servidor no tiene el formato esperado.',
        );
      }
      final codigo = aInt(cuerpo['codigo']);
      final mensaje = aString(cuerpo['mensaje']) ?? '';
      if (codigo != null && codigo != 0) {
        return Failure(
          tipo: codigo == 1 ? TipoFallo.validacion : TipoFallo.desconocido,
          httpStatus: status,
          codigo: codigo,
          mensaje: mensaje,
          campos: _campos(cuerpo['data']),
          data: cuerpo['data'],
        );
      }
      return Ok<T>(
        parser(cuerpo['data']),
        mensaje: mensaje,
        httpStatus: status,
      );
    } catch (_) {
      return Failure(
        tipo: TipoFallo.desconocido,
        httpStatus: status,
        mensaje: 'La respuesta del servidor no tiene el formato esperado.',
      );
    }
  }

  Failure _desdeDioException(DioException e) {
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.transformTimeout:
      case DioExceptionType.connectionError:
      case DioExceptionType.badCertificate:
        return const Failure(
          tipo: TipoFallo.red,
          mensaje: 'No se pudo conectar con el servidor.',
        );
      case DioExceptionType.badResponse:
        final respuesta = e.response;
        if (respuesta == null) {
          return const Failure(
            tipo: TipoFallo.desconocido,
            mensaje: 'Ocurrió un error inesperado.',
          );
        }
        return _desdeRespuestaError(respuesta);
      case DioExceptionType.cancel:
        return const Failure(
          tipo: TipoFallo.desconocido,
          mensaje: 'La petición fue cancelada.',
        );
      case DioExceptionType.unknown:
        if (e.error is FormatException) {
          return const Failure(
            tipo: TipoFallo.desconocido,
            mensaje: 'Ocurrió un error inesperado.',
          );
        }
        return const Failure(
          tipo: TipoFallo.red,
          mensaje: 'No se pudo conectar con el servidor.',
        );
    }
  }

  Failure _desdeRespuestaError(Response<Object?> respuesta) {
    final status = respuesta.statusCode ?? 0;
    final cuerpo = _normalizar(respuesta.data);
    int? codigo;
    var mensaje = '';
    var campos = const <CampoError>[];
    Object? datos;
    if (cuerpo is Map) {
      codigo = aInt(cuerpo['codigo']);
      mensaje = aString(cuerpo['mensaje']) ?? '';
      campos = _campos(cuerpo['data']);
      datos = cuerpo['data'];
    }
    final tipo = (codigo == 1 || status == 400)
        ? TipoFallo.validacion
        : _tipoPorEstado(status);
    int? retryAfter;
    if (status == 429) {
      retryAfter = int.tryParse(
        (respuesta.headers.value('retry-after') ?? '').trim(),
      );
    }
    return Failure(
      tipo: tipo,
      httpStatus: status,
      codigo: codigo,
      mensaje: mensaje,
      campos: campos,
      retryAfterSegundos: retryAfter,
      data: datos,
    );
  }

  TipoFallo _tipoPorEstado(int status) {
    if (status == 401) return TipoFallo.noAutenticado;
    if (status == 403) return TipoFallo.prohibido;
    if (status == 404) return TipoFallo.noEncontrado;
    if (status == 423) return TipoFallo.bloqueado;
    if (status == 429) return TipoFallo.demasiadosIntentos;
    if (status >= 500) return TipoFallo.servidor;
    return TipoFallo.desconocido;
  }

  List<CampoError> _campos(Object? data) {
    if (data is! List) return const [];
    final resultado = <CampoError>[];
    for (final item in data) {
      if (item is Map) {
        resultado.add(
          CampoError(
            campo: aString(item['campo']) ?? '',
            mensaje: aString(item['mensaje']) ?? '',
          ),
        );
      }
    }
    return resultado;
  }

  /// Devuelve el cuerpo decodificado (Map/List) o null si viene vacío.
  Object? _normalizar(Object? crudo) {
    if (crudo is String) {
      if (crudo.trim().isEmpty) return null;
      try {
        return jsonDecode(crudo);
      } catch (_) {
        return crudo;
      }
    }
    return crudo;
  }
}

/// Opciones base de Dio con los tiempos de espera por defecto.
BaseOptions opcionesBaseDio(String baseUrl) {
  return BaseOptions(
    baseUrl: baseUrl,
    connectTimeout: AppConfig.tiempoConexion,
    receiveTimeout: AppConfig.tiempoRecepcion,
    contentType: Headers.jsonContentType,
    responseType: ResponseType.json,
    headers: {'Accept': 'application/json'},
  );
}
