import '../../../../core/http/json_helpers.dart';
import '../../domain/entities/comprobante_pago.dart';
import '../../domain/entities/cuenta_cliente.dart';
import '../../domain/entities/solicitud_pago.dart';

String? _texto(Object? v) {
  final t = aString(v)?.trim();
  return (t == null || t.isEmpty) ? null : t;
}

/// Convierte un elemento de `data` de `/cuentas/mias`. Null si no es usable.
CuentaCliente? cuentaDesdeJson(Object? json) {
  if (json is! Map) return null;
  final estatus = _texto(json['estatus']);
  if (estatus != null && estatus.toUpperCase() != 'ACTIVA') return null;
  final numCuenta = _texto(json['numCuenta']);
  final saldo = aDouble(json['saldo']);
  if (numCuenta == null || saldo == null) return null;
  return CuentaCliente(
    numCuenta: numCuenta,
    numCuentaEnmascarada:
        _texto(json['numCuentaEnmascarada']) ?? _enmascarar(numCuenta),
    saldo: saldo,
  );
}

String _enmascarar(String numCuenta) {
  if (numCuenta.length <= 4) return numCuenta;
  final ultimos = numCuenta.substring(numCuenta.length - 4);
  return '${'*' * (numCuenta.length - 4)}$ultimos';
}

List<CuentaCliente> cuentasDesdeJson(Object? data) {
  if (data is! List) return const [];
  return [
    for (final item in data)
      if (cuentaDesdeJson(item) case final c?) c,
  ];
}

/// `PagoServicioResponse`; null si `json` no es un objeto.
ComprobantePago? comprobanteDesdeJson(Object? json) {
  if (json is! Map) return null;
  final motivo = _texto(json['motivo']);
  return ComprobantePago(
    estatus: EstatusPago.desdeTexto(aString(json['estatus'])),
    numCuenta: _texto(json['numCuenta']),
    upc: _texto(json['upc']),
    motivo: motivo == null ? null : MotivoPago.desdeTexto(motivo),
    monto: aDouble(json['monto']),
    saldoRestante: aDouble(json['saldoRestante']),
    folio: _texto(json['folio']),
    idTx: _texto(json['idTx']),
    identificadorOperacion: _texto(json['identificadorOperacion']),
    comision: aDouble(json['comision']),
    pinServicio: _texto(json['pinServicio']),
    leyenda: _texto(json['leyenda']),
    servicio: _texto(json['servicio']),
    producto: _texto(json['producto']),
    referenciaEnmascarada: _texto(json['referenciaEnmascarada']),
    telefonoEnmascarado: _texto(json['telefonoEnmascarado']),
    fechaTransaccion: _texto(json['fechaTransaccion']),
    idServicio: aInt(json['idServicio']),
    idProducto: aInt(json['idProducto']),
    logoUrl: _texto(json['logoUrl']),
  );
}

/// Detalle del `data` de un error de negocio.
class DetalleErrorModel {
  const DetalleErrorModel(
      {this.motivo, this.intentosRestantes, this.minutosBloqueo});

  final MotivoPago? motivo;
  final int? intentosRestantes;
  final int? minutosBloqueo;

  factory DetalleErrorModel.desdeJson(Object? data) {
    if (data is! Map) return const DetalleErrorModel();
    final motivo = _texto(data['motivo']);
    return DetalleErrorModel(
      motivo: motivo == null ? null : MotivoPago.desdeTexto(motivo),
      intentosRestantes: aInt(data['intentosRestantes']),
      minutosBloqueo: aInt(data['minutosBloqueo']),
    );
  }
}

/// Cuerpo de `POST /api/v1/pagos/servicio`. No se envían campos que no
/// correspondan (el backend rechaza los extra).
Map<String, Object?> solicitudAJson(SolicitudPago s) {
  return {
    'numCuenta': s.numCuenta,
    'idSer': s.idSer,
    'idProd': s.idProd,
    'monto': s.monto,
    'esReferencia': s.esReferencia,
    if (!s.esReferencia) 'telefono': s.telefono,
    if (s.esReferencia) 'reference': s.reference,
    if (s.esReferencia) 'idValidacion': s.idValidacion,
    'twoFA': s.twoFA,
  };
}
