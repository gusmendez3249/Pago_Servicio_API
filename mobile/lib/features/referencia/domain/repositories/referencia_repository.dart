import '../../../../core/http/result.dart';
import '../entities/validacion_referencia.dart';

abstract class ReferenciaRepository {
  Future<Result<ValidacionReferencia>> validar({
    required int idSer,
    required int idProd,
    required String referencia,
  });
}
