import '../../../../core/http/result.dart';
import '../../domain/entities/elemento_historial.dart';
import '../../domain/repositories/historial_repository.dart';
import '../datasources/historial_remote_datasource.dart';

class HistorialRepositoryImpl implements HistorialRepository {
  const HistorialRepositoryImpl(this._remoto);

  final HistorialRemoteDataSource _remoto;

  @override
  Future<Result<PaginaHistorial>> obtener({
    String? estatus,
    required int pagina,
    required int tamanio,
  }) {
    return _remoto.historial(
      estatus: estatus,
      pagina: pagina,
      tamanio: tamanio,
    );
  }
}
