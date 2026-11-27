import '../../../../core/http/result.dart';
import '../../domain/entities/validacion_referencia.dart';
import '../../domain/repositories/referencia_repository.dart';
import '../datasources/referencia_remote_datasource.dart';
import '../models/validacion_referencia_model.dart';

class ReferenciaRepositoryImpl implements ReferenciaRepository {
  const ReferenciaRepositoryImpl(this._remoto);

  final ReferenciaRemoteDataSource _remoto;

  @override
  Future<Result<ValidacionReferencia>> validar({
    required int idSer,
    required int idProd,
    required String referencia,
  }) async {
    final Result<ValidacionReferenciaModel> resultado = await _remoto.validar(
      ValidarReferenciaRequestModel(
        idSer: idSer,
        idProd: idProd,
        referencia: referencia,
      ),
    );
    if (resultado is Failure) return resultado;
    final ok = resultado as Ok<ValidacionReferenciaModel>;
    return Ok<ValidacionReferencia>(
      ok.data.entidad,
      mensaje: ok.mensaje,
      httpStatus: ok.httpStatus,
    );
  }
}
