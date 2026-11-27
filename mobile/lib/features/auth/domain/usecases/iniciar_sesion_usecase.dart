import '../../../../core/http/result.dart';
import '../entities/sesion_usuario.dart';
import '../repositories/auth_repository.dart';

class IniciarSesionUseCase {
  const IniciarSesionUseCase(this._repositorio);

  final AuthRepository _repositorio;

  Future<Result<SesionUsuario>> call({
    required String username,
    required String password,
  }) {
    return _repositorio.iniciarSesion(username: username, password: password);
  }
}
