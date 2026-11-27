import '../../../../core/http/api_client.dart';
import '../../../../core/http/result.dart';
import '../../domain/entities/elemento_historial.dart';
import '../models/historial_models.dart';

class HistorialRemoteDataSource {
  const HistorialRemoteDataSource(this._api);

  final ApiClient _api;

  /// `POST /api/v1/pagos/historial`.
  Future<Result<PaginaHistorial>> historial({
    String? estatus,
    required int pagina,
    required int tamanio,
  }) {
    return _api.post<PaginaHistorial>(
      '/api/v1/pagos/historial',
      body: solicitudHistorialAJson(
        estatus: estatus,
        pagina: pagina,
        tamanio: tamanio,
      ),
      parser: paginaHistorialDesdeJson,
    );
  }
}
