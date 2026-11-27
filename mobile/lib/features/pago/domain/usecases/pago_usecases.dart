import '../../../../core/http/result.dart';
import '../entities/cuenta_cliente.dart';
import '../entities/salida_pago.dart';
import '../entities/solicitud_pago.dart';
import '../repositories/pago_repository.dart';
import '../utils/interpretar_respuesta.dart';

class ObtenerCuentasUseCase {
  const ObtenerCuentasUseCase(this._repositorio);

  final PagoRepository _repositorio;

  Future<Result<List<CuentaCliente>>> call() => _repositorio.obtenerCuentas();
}

class PagarServicioUseCase {
  const PagarServicioUseCase(this._repositorio);

  final PagoRepository _repositorio;

  Future<SalidaPago> call(
    SolicitudPago solicitud, {
    required String idempotencyKey,
  }) async {
    final llamada = await _repositorio.pagar(
      solicitud,
      idempotencyKey: idempotencyKey,
    );
    return interpretarPago(llamada);
  }
}

class ConsultarEstadoPagoUseCase {
  const ConsultarEstadoPagoUseCase(this._repositorio);

  final PagoRepository _repositorio;

  Future<SalidaConsulta> call({String? upc, String? idempotencyKey}) async {
    final llamada = await _repositorio.consultarEstado(
      upc: upc,
      idempotencyKey: idempotencyKey,
    );
    return interpretarConsulta(llamada);
  }
}
