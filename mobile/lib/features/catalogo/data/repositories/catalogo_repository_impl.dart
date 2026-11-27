import '../../../../core/http/result.dart';
import '../../domain/entities/producto.dart';
import '../../domain/repositories/catalogo_repository.dart';
import '../datasources/catalogo_remote_datasource.dart';
import '../models/producto_model.dart';

class CatalogoRepositoryImpl implements CatalogoRepository {
  const CatalogoRepositoryImpl(this._remoto);

  final CatalogoRemoteDataSource _remoto;

  @override
  Future<Result<List<Producto>>> obtenerProductos() async {
    final Result<List<ProductoModel>> resultado =
        await _remoto.obtenerProductos();
    if (resultado is Failure) return resultado;
    final ok = resultado as Ok<List<ProductoModel>>;
    return Ok<List<Producto>>(
      ok.data.map((m) => m.entidad).toList(),
      mensaje: ok.mensaje,
      httpStatus: ok.httpStatus,
    );
  }
}
