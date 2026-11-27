import '../../../../core/http/result.dart';
import '../entities/validacion_referencia.dart';
import '../repositories/referencia_repository.dart';
import '../utils/formato_referencia.dart';

/// Valida una referencia contra el backend. Recorta la referencia antes de
/// enviarla; el formato se comprueba antes de llamar (ver
/// [validarFormatoReferencia]).
class ValidarReferenciaUseCase {
  const ValidarReferenciaUseCase(this._repositorio);

  final ReferenciaRepository _repositorio;

  Future<Result<ValidacionReferencia>> call({
    required int idSer,
    required int idProd,
    required String referencia,
  }) {
    return _repositorio.validar(
      idSer: idSer,
      idProd: idProd,
      referencia: recortarReferencia(referencia),
    );
  }
}
