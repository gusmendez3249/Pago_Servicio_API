import '../../../../core/http/result.dart';
import '../entities/cuenta_cliente.dart';
import '../entities/llamada_pago.dart';
import '../entities/solicitud_pago.dart';

abstract class PagoRepository {
  Future<Result<List<CuentaCliente>>> obtenerCuentas();

  /// Paga con la [idempotencyKey] dada (tiempo de espera largo).
  Future<LlamadaPago> pagar(
    SolicitudPago solicitud, {
    required String idempotencyKey,
  });

  /// Consulta por `upc` o, si no hay, por llave de idempotencia.
  Future<LlamadaPago> consultarEstado({String? upc, String? idempotencyKey});
}
