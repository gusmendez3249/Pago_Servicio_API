import '../entities/validacion_referencia.dart';

/// Tipos de front cuyos productos usan captura y validación de referencia.
const Set<int> kTiposFrontReferencia = {2, 4};

bool tipoFrontAdmiteReferencia(int? tipoFront) =>
    tipoFront != null && kTiposFrontReferencia.contains(tipoFront);

/// Regla del candado hacia el pago: hay una validación vigente (no expirada)
/// para ese mismo producto.
bool validacionVigenteParaPago(
  DatosPagoReferencia? datos, {
  required int idServicio,
  required int idProducto,
  required DateTime ahora,
}) {
  if (datos == null) return false;
  if (datos.idValidacion.isEmpty) return false;
  if (datos.idSer != idServicio || datos.idProd != idProducto) return false;
  return ahora.isBefore(datos.expiraEn);
}

/// Tipo de front de los productos con teléfono (sin validación de referencia).
const int kTipoFrontPagoTelefono = 1;

/// Guard `pagoPermitido` de la ruta de pago.
///
/// - tipoFront 1: exige `precio > 0` (no hay validación).
/// - resto: exige una validación de referencia vigente de ese producto.
bool pagoPermitido({
  required int? tipoFront,
  required double? precio,
  required DatosPagoReferencia? datos,
  required int idServicio,
  required int idProducto,
  required DateTime ahora,
}) {
  if (tipoFront == kTipoFrontPagoTelefono) {
    return (precio ?? 0) > 0;
  }
  return validacionVigenteParaPago(
    datos,
    idServicio: idServicio,
    idProducto: idProducto,
    ahora: ahora,
  );
}
