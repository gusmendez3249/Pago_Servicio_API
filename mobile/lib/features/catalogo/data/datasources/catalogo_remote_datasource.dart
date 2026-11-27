import '../../../../core/http/api_client.dart';
import '../../../../core/http/result.dart';
import '../models/producto_model.dart';

class CatalogoRemoteDataSource {
  const CatalogoRemoteDataSource(this._api);

  final ApiClient _api;

  /// `GET /productos` (sin token).
  Future<Result<List<ProductoModel>>> obtenerProductos() {
    return _api.get<List<ProductoModel>>(
      '/productos',
      autenticada: false,
      parser: (data) {
        if (data == null) return <ProductoModel>[];
        if (data is! List) {
          throw const FormatException('Se esperaba una lista de productos.');
        }
        return data
            .whereType<Map<dynamic, dynamic>>()
            .map((e) => ProductoModel.fromJson(Map<String, dynamic>.from(e)))
            .toList();
      },
    );
  }
}
