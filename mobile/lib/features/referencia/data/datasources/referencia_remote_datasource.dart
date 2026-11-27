import '../../../../core/http/api_client.dart';
import '../../../../core/http/result.dart';
import '../models/validacion_referencia_model.dart';

class ReferenciaRemoteDataSource {
  const ReferenciaRemoteDataSource(this._api);

  final ApiClient _api;

  /// `POST /api/v1/pagos/referencia/validar` (requiere sesión).
  Future<Result<ValidacionReferenciaModel>> validar(
    ValidarReferenciaRequestModel peticion,
  ) {
    return _api.post<ValidacionReferenciaModel>(
      '/api/v1/pagos/referencia/validar',
      body: peticion.toJson(),
      parser: (data) => ValidacionReferenciaModel.fromJson(
        Map<String, dynamic>.from(data as Map),
      ),
    );
  }
}
