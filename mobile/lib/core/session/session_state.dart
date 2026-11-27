import 'rol_usuario.dart';

class SesionState {
  const SesionState({
    this.token,
    this.username,
    this.nombreCliente,
    this.rol,
    this.avisoSesion,
  });

  static const SesionState sinSesion = SesionState();

  final String? token;
  final String? username;
  final String? nombreCliente;
  final RolUsuario? rol;

  /// Aviso para mostrar en el login (p. ej. sesión expirada por inactividad).
  final String? avisoSesion;

  bool get autenticada => token != null && token!.isNotEmpty;
}
