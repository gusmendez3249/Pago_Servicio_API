import '../../../../core/http/json_helpers.dart';
import '../../../../core/session/rol_usuario.dart';
import '../../domain/entities/sesion_usuario.dart';

class LoginResponseModel {
  const LoginResponseModel({
    required this.username,
    required this.nombreCliente,
    required this.token,
    required this.rol,
    required this.expiraEnMinutos,
  });

  final String username;
  final String nombreCliente;
  final String token;
  final String rol;
  final int expiraEnMinutos;

  /// Lanza [FormatException] si falta el token (el ApiClient lo convierte en fallo).
  factory LoginResponseModel.fromJson(Map<String, dynamic> json) {
    final token = aString(json['token']);
    if (token == null || token.isEmpty) {
      throw const FormatException('La respuesta de login no incluye token.');
    }
    return LoginResponseModel(
      username: aString(json['username']) ?? '',
      nombreCliente: aString(json['nombreCliente']) ?? '',
      token: token,
      rol: aString(json['rol']) ?? 'CLIENTE',
      expiraEnMinutos: aInt(json['expiraEnMinutos']) ?? 5,
    );
  }

  SesionUsuario toEntity() => SesionUsuario(
        username: username,
        nombreCliente: nombreCliente,
        token: token,
        rol: RolUsuario.desdeTexto(rol),
        expiraEnMinutos: expiraEnMinutos,
      );
}
