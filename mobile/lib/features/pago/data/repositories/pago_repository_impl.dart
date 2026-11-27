import '../../../../core/http/result.dart';
import '../../domain/entities/comprobante_pago.dart';
import '../../domain/entities/cuenta_cliente.dart';
import '../../domain/entities/llamada_pago.dart';
import '../../domain/entities/solicitud_pago.dart';
import '../../domain/repositories/pago_repository.dart';
import '../datasources/pago_remote_datasource.dart';
import '../models/pago_models.dart';

class PagoRepositoryImpl implements PagoRepository {
  const PagoRepositoryImpl(this._remoto);

  final PagoRemoteDataSource _remoto;

  @override
  Future<Result<List<CuentaCliente>>> obtenerCuentas() => _remoto.cuentas();

  @override
  Future<LlamadaPago> pagar(
    SolicitudPago solicitud, {
    required String idempotencyKey,
  }) async {
    return _traducir(
      await _remoto.pagar(solicitud, idempotencyKey: idempotencyKey),
    );
  }

  @override
  Future<LlamadaPago> consultarEstado(
      {String? upc, String? idempotencyKey}) async {
    return _traducir(
      await _remoto.estado(upc: upc, idempotencyKey: idempotencyKey),
    );
  }

  LlamadaPago _traducir(Result<ComprobantePago?> resultado) {
    switch (resultado) {
      case Ok(:final data, :final httpStatus):
        return LlamadaOk(httpStatus: httpStatus, comprobante: data);
      case Failure():
        final detalle = DetalleErrorModel.desdeJson(resultado.data);
        // En un 422 `data` es el PagoServicioResponse completo.
        final comprobante = resultado.httpStatus == 422
            ? comprobanteDesdeJson(resultado.data)
            : null;
        return LlamadaFallo(
          tipo: resultado.tipo,
          httpStatus: resultado.httpStatus,
          motivo: detalle.motivo,
          intentosRestantes: detalle.intentosRestantes,
          minutosBloqueo: detalle.minutosBloqueo,
          comprobante: comprobante,
        );
    }
  }
}
