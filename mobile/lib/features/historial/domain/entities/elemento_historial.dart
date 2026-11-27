import '../../../pago/domain/entities/comprobante_pago.dart';

/// Una operación de la lista del historial (sin PIN ni saldo: la lista nunca
/// los trae).
class ElementoHistorial {
  const ElementoHistorial({
    required this.estatus,
    this.upc,
    this.monto,
    this.folio,
    this.identificadorOperacion,
    this.servicio,
    this.producto,
    this.referenciaEnmascarada,
    this.telefonoEnmascarado,
    this.fechaTransaccion,
    this.idServicio,
    this.idProducto,
    this.logoUrl,
  });

  final EstatusPago estatus;
  final String? upc;
  final double? monto;
  final String? folio;
  final String? identificadorOperacion;
  final String? servicio;
  final String? producto;
  final String? referenciaEnmascarada;
  final String? telefonoEnmascarado;

  /// Fecha y hora tal como llegan del servidor (ISO 8601).
  final String? fechaTransaccion;
  final int? idServicio;
  final int? idProducto;
  final String? logoUrl;
}

/// Una página de resultados del historial.
class PaginaHistorial {
  const PaginaHistorial({
    required this.elementos,
    required this.pagina,
    required this.tamanio,
    required this.total,
    required this.hayMas,
  });

  final List<ElementoHistorial> elementos;
  final int pagina;
  final int tamanio;
  final int total;
  final bool hayMas;
}
