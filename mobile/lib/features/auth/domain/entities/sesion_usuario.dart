import '../../../../core/session/rol_usuario.dart';

class SesionUsuario {
  const SesionUsuario({
    required this.username,
    required this.nombreCliente,
    required this.token,
    required this.rol,
    required this.expiraEnMinutos,
  });

  final String username;
  final String nombreCliente;
  final String token;
  final RolUsuario rol;
  final int expiraEnMinutos;
}
