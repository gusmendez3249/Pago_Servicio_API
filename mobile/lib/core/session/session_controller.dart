import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/app_config.dart';
import 'almacen_sesion.dart';
import 'almacen_sesion_seguro.dart';
import 'rol_usuario.dart';
import 'session_state.dart';

/// En web el token nunca se persiste (solo memoria); en móvil, Keystore/Keychain.
AlmacenSesion crearAlmacenSesion({bool esWeb = kIsWeb}) =>
    esWeb ? AlmacenSesionMemoria() : AlmacenSesionSeguro();

final almacenSesionProvider = Provider<AlmacenSesion>(
  (ref) => crearAlmacenSesion(),
);

/// Reloj inyectable (se sustituye en pruebas).
final relojProvider = Provider<DateTime Function()>((ref) => DateTime.now);

final sessionControllerProvider =
    NotifierProvider<SessionController, SesionState>(SessionController.new);

class SessionController extends Notifier<SesionState> {
  Timer? _temporizador;
  DateTime? _ultimaActividad;

  AlmacenSesion get _almacen => ref.read(almacenSesionProvider);
  DateTime _ahora() => ref.read(relojProvider)();

  @override
  SesionState build() {
    ref.onDispose(_cancelarTemporizador);
    return SesionState.sinSesion;
  }

  /// Recupera la sesión guardada (si no expiró por inactividad).
  Future<void> restaurar() async {
    DatosSesionGuardada? datos;
    try {
      datos = await _almacen.leer();
    } catch (_) {
      datos = null;
    }
    if (datos == null) return;

    final inactivo = _ahora().difference(datos.ultimaActividad);
    if (inactivo >= AppConfig.inactividadMaxima) {
      await _borrarAlmacen();
      state = const SesionState(avisoSesion: AppConfig.mensajeSesionExpirada);
      return;
    }
    _ultimaActividad = datos.ultimaActividad;
    state = SesionState(
      token: datos.token,
      username: datos.username,
      nombreCliente: datos.nombreCliente,
      rol: RolUsuario.desdeTexto(datos.rol),
    );
    _programarTemporizador(AppConfig.inactividadMaxima - inactivo);
  }

  Future<void> iniciarSesion({
    required String token,
    required String username,
    required String nombreCliente,
    required RolUsuario rol,
  }) async {
    final ahora = _ahora();
    _ultimaActividad = ahora;
    state = SesionState(
      token: token,
      username: username,
      nombreCliente: nombreCliente,
      rol: rol,
    );
    _programarTemporizador(AppConfig.inactividadMaxima);
    try {
      await _almacen.guardar(
        DatosSesionGuardada(
          token: token,
          username: username,
          nombreCliente: nombreCliente,
          rol: rol == RolUsuario.ejecutivo ? 'EJECUTIVO' : 'CLIENTE',
          ultimaActividad: ahora,
        ),
      );
    } catch (_) {
      // Si no se puede persistir, la sesión sigue viva solo en memoria.
    }
  }

  /// Se llama tras cada petición autenticada exitosa.
  void registrarActividad() {
    if (!state.autenticada) return;
    final ahora = _ahora();
    _ultimaActividad = ahora;
    _programarTemporizador(AppConfig.inactividadMaxima);
    unawaited(_persistirActividad(ahora));
  }

  /// Al volver de segundo plano: si pasaron 5 min sin actividad, cierra sesión.
  Future<void> alVolverDeSegundoPlano() async {
    if (!state.autenticada) return;
    final ultima = _ultimaActividad;
    if (ultima == null) return;
    if (_ahora().difference(ultima) >= AppConfig.inactividadMaxima) {
      await expirarPorInactividad();
    }
  }

  Future<void> expirarPorInactividad() {
    return _cerrarLocal(aviso: AppConfig.mensajeSesionExpirada);
  }

  /// El backend respondió 401 a una petición autenticada.
  Future<void> invalidarPorNoAutorizado() {
    return _cerrarLocal(
      aviso: 'Tu sesión ya no es válida. Inicia sesión de nuevo.',
    );
  }

  /// 403 de una petición autenticada con cuenta/cliente inactivo.
  Future<void> invalidarPorCuentaInactiva() {
    return _cerrarLocal(aviso: AppConfig.mensajeCuentaInactiva);
  }

  /// Cierre voluntario: borra estado y almacenamiento seguro.
  Future<void> cerrarSesion() => _cerrarLocal();

  Future<void> _cerrarLocal({String? aviso}) async {
    _cancelarTemporizador();
    _ultimaActividad = null;
    state = SesionState(avisoSesion: aviso);
    await _borrarAlmacen();
  }

  Future<void> _borrarAlmacen() async {
    try {
      await _almacen.borrar();
    } catch (_) {
      // Nada más que hacer: el estado en memoria ya quedó limpio.
    }
  }

  Future<void> _persistirActividad(DateTime actividad) async {
    try {
      final actual = await _almacen.leer();
      if (actual != null && state.autenticada) {
        await _almacen.guardar(actual.conActividad(actividad));
      }
    } catch (_) {
      // Ignorar fallos de persistencia.
    }
  }

  void _programarTemporizador(Duration restante) {
    _cancelarTemporizador();
    _temporizador = Timer(restante, () {
      unawaited(expirarPorInactividad());
    });
  }

  void _cancelarTemporizador() {
    _temporizador?.cancel();
    _temporizador = null;
  }
}
