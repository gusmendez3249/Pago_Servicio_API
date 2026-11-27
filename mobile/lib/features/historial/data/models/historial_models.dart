import '../../../../core/http/json_helpers.dart';
import '../../../pago/domain/entities/comprobante_pago.dart';
import '../../domain/entities/elemento_historial.dart';

String? _texto(Object? v) {
  final t = aString(v)?.trim();
  return (t == null || t.isEmpty) ? null : t;
}

/// Convierte un `ElementoHistorial` tolerando nulos y tipos distintos.
/// `codigoRespuesta`, `mensajeRespuesta` y `pinServicio` se ignoran a propósito.
ElementoHistorial? elementoDesdeJson(Object? json) {
  if (json is! Map) return null;
  return ElementoHistorial(
    estatus: EstatusPago.desdeTexto(aString(json['estatus'])),
    upc: _texto(json['upc']),
    monto: aDouble(json['monto']),
    folio: _texto(json['folio']),
    identificadorOperacion: _texto(json['identificadorOperacion']),
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

/// `data` de `/pagos/historial`. Lanza [FormatException] si no es un objeto
/// (el cliente HTTP lo convierte en un fallo).
PaginaHistorial paginaHistorialDesdeJson(Object? data) {
  if (data is! Map) throw const FormatException('historial');
  final crudos = data['elementos'];
  final elementos = <ElementoHistorial>[
    if (crudos is List)
      for (final item in crudos)
        if (elementoDesdeJson(item) case final e?) e,
  ];
  return PaginaHistorial(
    elementos: elementos,
    pagina: aInt(data['pagina']) ?? 0,
    tamanio: aInt(data['tamanio']) ?? elementos.length,
    total: aInt(data['total']) ?? elementos.length,
    hayMas: aBool(data['hayMas']) ?? false,
  );
}

/// Cuerpo de la solicitud; `filtros` solo se envía si hay un estatus.
Map<String, Object?> solicitudHistorialAJson({
  String? estatus,
  required int pagina,
  required int tamanio,
}) {
  return {
    if (estatus != null) 'filtros': {'estatus': estatus},
    'pagina': pagina,
    'tamanio': tamanio,
  };
}
