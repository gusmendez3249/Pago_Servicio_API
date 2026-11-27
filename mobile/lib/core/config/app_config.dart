import 'package:flutter/foundation.dart';

/// Hosts locales en los que se tolera `http` (emulador y pruebas en la misma máquina).
const _hostsLocales = {'localhost', '127.0.0.1', '10.0.2.2', '[::1]', '::1'};

/// Configuración de la app. La URL del backend se inyecta SOLO en tiempo de
/// compilación con `--dart-define=API_BASE_URL=...` (no hay valor por defecto).
class AppConfig {
  const AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment('API_BASE_URL');

  static bool get configuracionValida =>
      urlValida(apiBaseUrl, release: kReleaseMode);

  /// En release solo se acepta `https`, salvo un host local; así el token y la contraseña
  /// nunca viajan sin cifrar por una red real.
  @visibleForTesting
  static bool urlValida(String url, {required bool release}) {
    final uri = Uri.tryParse(url.trim());
    if (uri == null || !uri.hasScheme || uri.host.isEmpty) return false;
    if (uri.scheme == 'https') return true;
    if (uri.scheme != 'http') return false;
    return !release || _hostsLocales.contains(uri.host);
  }

  /// El token del backend vence tras 5 minutos sin actividad.
  static const Duration inactividadMaxima = Duration(minutes: 5);

  static const Duration tiempoConexion = Duration(seconds: 10);
  static const Duration tiempoRecepcion = Duration(seconds: 15);

  static const String mensajeSesionExpirada =
      'Tu sesión expiró por inactividad';

  static const String mensajeCuentaInactiva =
      'Tu cuenta está inactiva. Comunícate con tu ejecutivo para '
      'reactivarla.';
}
