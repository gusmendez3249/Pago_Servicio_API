import '../../../../core/http/result.dart';
import '../../domain/entities/sesion_usuario.dart';
import '../../domain/repositories/auth_repository.dart';
import '../datasources/auth_remote_datasource.dart';
import '../models/login_request_model.dart';
import '../models/login_response_model.dart';

class AuthRepositoryImpl implements AuthRepository {
  const AuthRepositoryImpl(this._remoto);

  final AuthRemoteDataSource _remoto;

  @override
  Future<Result<SesionUsuario>> iniciarSesion({
    required String username,
    required String password,
  }) async {
    final Result<LoginResponseModel> resultado = await _remoto.login(
      LoginRequestModel(username: username, password: password),
    );
    if (resultado is Failure) return resultado;
    final ok = resultado as Ok<LoginResponseModel>;
    return Ok<SesionUsuario>(
      ok.data.toEntity(),
      mensaje: ok.mensaje,
      httpStatus: ok.httpStatus,
    );
  }

  @override
  Future<Result<void>> cerrarSesion() async {
    final Result<void> resultado = await _remoto.logout();
    if (resultado is Failure && resultado.tipo == TipoFallo.noAutenticado) {
      // Token ya vencido: el objetivo (no tener sesión) ya se cumplió.
      return const Ok<void>(null, httpStatus: 401);
    }
    return resultado;
  }
}
