import '../../../../core/http/result.dart';
import '../entities/sesion_usuario.dart';

abstract interface class AuthRepository {
  Future<Result<SesionUsuario>> iniciarSesion({
    required String username,
    required String password,
  });

  /// Un 401 (token ya vencido) se considera éxito.
  Future<Result<void>> cerrarSesion();
}
