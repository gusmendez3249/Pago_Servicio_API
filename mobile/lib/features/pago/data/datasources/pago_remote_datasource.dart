import '../../../../core/http/api_client.dart';
import '../../../../core/http/result.dart';
import '../../domain/entities/comprobante_pago.dart';
import '../../domain/entities/cuenta_cliente.dart';
import '../../domain/entities/solicitud_pago.dart';
import '../../domain/utils/reglas_pago.dart';
import '../models/pago_models.dart';

class PagoRemoteDataSource {
  const PagoRemoteDataSource(this._api);

  final ApiClient _api;

  /// `POST /api/v1/cuentas/mias`.
  Future<Result<List<CuentaCliente>>> cuentas() {
    return _api.post<List<CuentaCliente>>(
      '/api/v1/cuentas/mias',
      body: const <String, Object?>{},
      parser: cuentasDesdeJson,
    );
  }

  /// `POST /api/v1/pagos/servicio` con `Idempotency-Key` y espera de 75 s.
  Future<Result<ComprobantePago?>> pagar(
    SolicitudPago solicitud, {
    required String idempotencyKey,
  }) {
    return _api.post<ComprobantePago?>(
      '/api/v1/pagos/servicio',
      body: solicitudAJson(solicitud),
      headers: {'Idempotency-Key': idempotencyKey},
      receiveTimeout: kTiempoEsperaPago,
      parser: comprobanteDesdeJson,
    );
  }

  /// `POST /api/v1/pagos/estado` con exactamente uno de `upc` o llave.
  Future<Result<ComprobantePago?>> estado(
      {String? upc, String? idempotencyKey}) {
    return _api.post<ComprobantePago?>(
      '/api/v1/pagos/estado',
      body: upc != null ? {'upc': upc} : {'idempotencyKey': idempotencyKey},
      parser: comprobanteDesdeJson,
    );
  }
}
