import '../../../../core/http/result.dart';
import '../repositories/auth_repository.dart';

class CerrarSesionUseCase {
  const CerrarSesionUseCase(this._repositorio);

  final AuthRepository _repositorio;

  Future<Result<void>> call() => _repositorio.cerrarSesion();
}
