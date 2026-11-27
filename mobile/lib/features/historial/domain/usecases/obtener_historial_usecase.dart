import '../../../../core/http/result.dart';
import '../entities/elemento_historial.dart';
import '../repositories/historial_repository.dart';

class ObtenerHistorialUseCase {
  const ObtenerHistorialUseCase(this._repositorio);

  final HistorialRepository _repositorio;

  Future<Result<PaginaHistorial>> call({
    String? estatus,
    required int pagina,
    required int tamanio,
  }) {
    return _repositorio.obtener(
      estatus: estatus,
      pagina: pagina,
      tamanio: tamanio,
    );
  }
}
