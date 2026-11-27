import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/error/mensajes_error.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/core/interceptors/sesion_interceptor.dart';
import 'package:gesto_pago_mobile/core/session/almacen_sesion.dart';
import 'package:gesto_pago_mobile/core/session/almacen_sesion_seguro.dart';
import 'package:gesto_pago_mobile/core/session/session_controller.dart';

class _Adaptador implements HttpClientAdapter {
  _Adaptador(this.status, this.cuerpo);
  final int status;
  final Object cuerpo;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async =>
      ResponseBody.fromString(
        jsonEncode(cuerpo),
        status,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );

  @override
  void close({bool force = false}) {}
}

Future<({int inactiva, int noAutorizado})> _llamar(
  int status,
  Object cuerpo, {
  bool autenticada = true,
}) async {
  var inactiva = 0;
  var noAutorizado = 0;
  final dio = Dio(BaseOptions(baseUrl: 'http://x'))
    ..httpClientAdapter = _Adaptador(status, cuerpo)
    ..interceptors.add(
      SesionInterceptor(
        alActividad: () {},
        alNoAutorizado: () => noAutorizado++,
        alCuentaInactiva: () => inactiva++,
      ),
    );
  try {
    await dio.get<Object?>(
      '/x',
      options: Options(
        headers: autenticada ? {'Authorization': 'Bearer t'} : null,
        extra: {'autenticada': autenticada},
      ),
    );
  } on DioException {
    // esperado
  }
  return (inactiva: inactiva, noAutorizado: noAutorizado);
}

void main() {
  group('almacén de sesión según plataforma', () {
    test('web usa memoria (el token no toca localStorage)', () async {
      final a = crearAlmacenSesion(esWeb: true);
      expect(a, isA<AlmacenSesionMemoria>());
      await a.guardar(
        DatosSesionGuardada(
          token: 't',
          username: 'u',
          nombreCliente: 'n',
          rol: 'CLIENTE',
          ultimaActividad: DateTime(2026),
        ),
      );
      expect((await a.leer())?.token, 't');
      await a.borrar();
      expect(await a.leer(), isNull);
      // Una instancia nueva (recarga de la página) no recuerda nada.
      expect(await crearAlmacenSesion(esWeb: true).leer(), isNull);
    });

    test('móvil usa el almacenamiento seguro', () {
      expect(crearAlmacenSesion(esWeb: false), isA<AlmacenSesionSeguro>());
    });
  });

  group('cuenta inactiva a mitad de sesión', () {
    test('403 CLIENTE_INACTIVO y CUENTA_INACTIVA cierran la sesión', () async {
      for (final motivo in ['CLIENTE_INACTIVO', 'CUENTA_INACTIVA']) {
        final r = await _llamar(403, {
          'codigo': 403,
          'mensaje': 'x',
          'data': {'motivo': motivo},
        });
        expect(r.inactiva, 1, reason: motivo);
        expect(r.noAutorizado, 0);
      }
    });

    test('otros 403 no cierran la sesión', () async {
      final r = await _llamar(403, {
        'codigo': 403,
        'data': {'motivo': 'PIN_INCORRECTO'},
      });
      expect(r.inactiva, 0);
    });

    test('sin autenticación no aplica (login)', () async {
      final r = await _llamar(
        403,
        {
          'codigo': 403,
          'data': {'motivo': 'CLIENTE_INACTIVO'},
        },
        autenticada: false,
      );
      expect(r.inactiva, 0);
    });
  });

  group('textos de login iguales a la web', () {
    test('403, 423 y 429', () {
      const p = Failure(tipo: TipoFallo.prohibido, mensaje: '');
      expect(
        mensajeDeFallo(p, esLogin: true),
        'Tu cuenta está inactiva. Comunícate con tu ejecutivo para reactivarla.',
      );
      expect(
        mensajeDeFallo(const Failure(tipo: TipoFallo.bloqueado, mensaje: '')),
        'Tu cuenta está bloqueada. Comunícate con tu ejecutivo para desbloquearla.',
      );
      expect(
        mensajeDeFallo(
          const Failure(
            tipo: TipoFallo.demasiadosIntentos,
            mensaje: '',
            retryAfterSegundos: 30,
          ),
        ),
        'Demasiados intentos. Espera 30 segundos e inténtalo de nuevo.',
      );
      expect(
        mensajeDeFallo(
          const Failure(tipo: TipoFallo.demasiadosIntentos, mensaje: ''),
        ),
        'Demasiados intentos. Espera un momento e inténtalo de nuevo.',
      );
    });
  });
}
