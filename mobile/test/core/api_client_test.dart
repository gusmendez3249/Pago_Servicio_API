import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/http/api_client.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';

class AdaptadorFalso implements HttpClientAdapter {
  AdaptadorFalso(this.responder);

  final Future<ResponseBody> Function(RequestOptions opciones) responder;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) =>
      responder(options);

  @override
  void close({bool force = false}) {}
}

ResponseBody _json(Object cuerpo, int status, {Map<String, List<String>>? extra}) {
  return ResponseBody.fromString(
    jsonEncode(cuerpo),
    status,
    headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
      ...?extra,
    },
  );
}

ApiClient _cliente(Future<ResponseBody> Function(RequestOptions) responder) {
  final dio = Dio(BaseOptions(baseUrl: 'http://prueba.local'));
  dio.httpClientAdapter = AdaptadorFalso(responder);
  return ApiClient(dio);
}

void main() {
  test('respuesta exitosa desempaqueta el envoltorio', () async {
    final api = _cliente(
      (_) async => _json({'codigo': 0, 'mensaje': 'ok', 'data': 'hola'}, 200),
    );
    final r = await api.get<String>('/x', parser: (d) => d as String);
    expect(r, isA<Ok<String>>());
    final ok = r as Ok<String>;
    expect(ok.data, 'hola');
    expect(ok.mensaje, 'ok');
    expect(ok.httpStatus, 200);
  });

  test('error de validacion (codigo 1) trae campos', () async {
    final api = _cliente(
      (_) async => _json({
        'codigo': 1,
        'mensaje': 'Datos inválidos',
        'data': [
          {'campo': 'username', 'mensaje': 'Obligatorio'},
        ],
      }, 400),
    );
    final r = await api.post<String>('/x', parser: (d) => d as String);
    expect(r, isA<Failure>());
    final f = r as Failure;
    expect(f.tipo, TipoFallo.validacion);
    expect(f.codigo, 1);
    expect(f.campos.single.campo, 'username');
    expect(f.campos.single.mensaje, 'Obligatorio');
  });

  test('401 se mapea a noAutenticado', () async {
    final api = _cliente(
      (_) async => _json({'codigo': 401, 'mensaje': 'No autorizado', 'data': null}, 401),
    );
    final r = await api.get<String>('/x', parser: (d) => d as String);
    expect((r as Failure).tipo, TipoFallo.noAutenticado);
    expect(r.httpStatus, 401);
  });

  test('429 lee Retry-After', () async {
    final api = _cliente(
      (_) async => _json(
        {'codigo': 429, 'mensaje': 'Muchos intentos', 'data': null},
        429,
        extra: {'retry-after': ['30']},
      ),
    );
    final r = await api.post<String>('/x', parser: (d) => d as String);
    final f = r as Failure;
    expect(f.tipo, TipoFallo.demasiadosIntentos);
    expect(f.retryAfterSegundos, 30);
  });

  test('timeout se mapea a red', () async {
    final api = _cliente(
      (opciones) async => throw DioException(
        requestOptions: opciones,
        type: DioExceptionType.connectionTimeout,
      ),
    );
    final r = await api.get<String>('/x', parser: (d) => d as String);
    expect((r as Failure).tipo, TipoFallo.red);
  });
}
