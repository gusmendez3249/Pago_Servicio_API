import '../../../../core/http/result.dart';
import '../entities/producto.dart';
import '../repositories/catalogo_repository.dart';

/// Obtiene el catálogo. Un 404 (sin productos) se trata como lista vacía.
class ObtenerCatalogoUseCase {
  const ObtenerCatalogoUseCase(this._repositorio);

  final CatalogoRepository _repositorio;

  Future<Result<List<Producto>>> call() async {
    final resultado = await _repositorio.obtenerProductos();
    if (resultado is Failure && resultado.tipo == TipoFallo.noEncontrado) {
      return const Ok<List<Producto>>(<Producto>[]);
    }
    return resultado;
  }
}
