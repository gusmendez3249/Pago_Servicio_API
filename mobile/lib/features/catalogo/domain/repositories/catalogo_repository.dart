import '../../../../core/http/result.dart';
import '../entities/producto.dart';

abstract interface class CatalogoRepository {
  Future<Result<List<Producto>>> obtenerProductos();
}
