import '../../../../core/http/result.dart';
import '../entities/elemento_historial.dart';

abstract class HistorialRepository {
  /// Pide una página del historial (`POST /api/v1/pagos/historial`).
  Future<Result<PaginaHistorial>> obtener({
    String? estatus,
    required int pagina,
    required int tamanio,
  });
}
