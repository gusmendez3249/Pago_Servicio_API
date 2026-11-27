import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'almacen_sesion.dart';

/// Implementación sobre `flutter_secure_storage` (Keystore en Android).
class AlmacenSesionSeguro implements AlmacenSesion {
  AlmacenSesionSeguro([FlutterSecureStorage? almacen])
      : _almacen = almacen ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  static const String _clave = 'sesion_gesto_pago';

  final FlutterSecureStorage _almacen;

  @override
  Future<DatosSesionGuardada?> leer() async {
    final crudo = await _almacen.read(key: _clave);
    if (crudo == null || crudo.isEmpty) return null;
    try {
      final mapa = jsonDecode(crudo);
      if (mapa is! Map) return null;
      final token = mapa['token'];
      final actividad = mapa['ultimaActividad'];
      if (token is! String || token.isEmpty || actividad is! int) return null;
      return DatosSesionGuardada(
        token: token,
        username: (mapa['username'] as String?) ?? '',
        nombreCliente: (mapa['nombreCliente'] as String?) ?? '',
        rol: (mapa['rol'] as String?) ?? 'CLIENTE',
        ultimaActividad: DateTime.fromMillisecondsSinceEpoch(actividad),
      );
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> guardar(DatosSesionGuardada datos) {
    final json = jsonEncode({
      'token': datos.token,
      'username': datos.username,
      'nombreCliente': datos.nombreCliente,
      'rol': datos.rol,
      'ultimaActividad': datos.ultimaActividad.millisecondsSinceEpoch,
    });
    return _almacen.write(key: _clave, value: json);
  }

  @override
  Future<void> borrar() => _almacen.delete(key: _clave);
}
