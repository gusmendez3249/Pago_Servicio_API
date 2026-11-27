import 'dart:async';

import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/comprobante_pago.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/cuenta_cliente.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/llamada_pago.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/solicitud_pago.dart';
import 'package:gesto_pago_mobile/features/pago/domain/repositories/pago_repository.dart';

const CuentaCliente cuentaRica = CuentaCliente(
  numCuenta: '695429041188',
  numCuentaEnmascarada: '********1188',
  saldo: 1000,
);

const CuentaCliente cuentaPobre = CuentaCliente(
  numCuenta: '695429041188',
  numCuentaEnmascarada: '********1188',
  saldo: 10,
);

/// Repositorio escrito a mano: registra las llamadas y responde con lo que se
/// le configure.
class RepoPagoFalso implements PagoRepository {
  RepoPagoFalso({this.cuentas = const [cuentaRica]});

  List<CuentaCliente> cuentas;
  int llamadasCuentas = 0;

  final List<SolicitudPago> pagos = [];
  final List<String> llaves = [];

  /// Respuestas de pago en orden; la última se repite.
  List<LlamadaPago> respuestasPago = [];

  /// Si no es null, el pago espera a que se complete.
  Completer<void>? compuertaPago;

  final List<({String? upc, String? llave})> consultas = [];
  List<LlamadaPago> respuestasEstado = [];

  @override
  Future<Result<List<CuentaCliente>>> obtenerCuentas() async {
    llamadasCuentas++;
    return Ok<List<CuentaCliente>>(cuentas);
  }

  @override
  Future<LlamadaPago> pagar(
    SolicitudPago solicitud, {
    required String idempotencyKey,
  }) async {
    pagos.add(solicitud);
    llaves.add(idempotencyKey);
    final c = compuertaPago;
    if (c != null) await c.future;
    final i = pagos.length - 1;
    return respuestasPago[i < respuestasPago.length ? i : respuestasPago.length - 1];
  }

  @override
  Future<LlamadaPago> consultarEstado({String? upc, String? idempotencyKey}) async {
    consultas.add((upc: upc, llave: idempotencyKey));
    final i = consultas.length - 1;
    return respuestasEstado[i < respuestasEstado.length ? i : respuestasEstado.length - 1];
  }
}

ComprobantePago comprobante(
  EstatusPago estatus, {
  String? folio,
  String? upc = 'K3J9X0A7ZQ',
  MotivoPago? motivo,
  String? pinServicio,
  String? leyenda,
  double monto = 100,
}) {
  return ComprobantePago(
    estatus: estatus,
    numCuenta: '********1188',
    upc: upc,
    motivo: motivo,
    monto: monto,
    folio: folio,
    identificadorOperacion: folio ?? upc,
    servicio: 'Telcel',
    producto: 'Recarga',
    telefonoEnmascarado: '******5678',
    pinServicio: pinServicio,
    leyenda: leyenda,
  );
}

LlamadaOk ok200(ComprobantePago c) => LlamadaOk(httpStatus: 200, comprobante: c);
LlamadaOk ok202(ComprobantePago c) => LlamadaOk(httpStatus: 202, comprobante: c);

LlamadaFallo fallo(
  int status, {
  TipoFallo tipo = TipoFallo.desconocido,
  MotivoPago? motivo,
  int? intentos,
  ComprobantePago? comprobante,
}) {
  return LlamadaFallo(
    tipo: tipo,
    httpStatus: status,
    motivo: motivo,
    intentosRestantes: intentos,
    comprobante: comprobante,
  );
}

const LlamadaFallo sinRed = LlamadaFallo(tipo: TipoFallo.red);
