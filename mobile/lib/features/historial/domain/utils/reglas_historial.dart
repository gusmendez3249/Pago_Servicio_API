import '../../../pago/domain/entities/comprobante_pago.dart';
import '../entities/elemento_historial.dart';

/// Cómo se presenta el estado de una operación.
enum EstadoVisual { exitoso, rechazado, pendiente }

/// CONFIRMADA es exitoso; RECHAZADA es rechazado; INDETERMINADA, PENDIENTE y
/// cualquier valor desconocido son pendiente (nunca se supone un éxito).
EstadoVisual estadoVisualDe(EstatusPago estatus) {
  switch (estatus) {
    case EstatusPago.confirmada:
      return EstadoVisual.exitoso;
    case EstatusPago.rechazada:
      return EstadoVisual.rechazado;
    case EstatusPago.indeterminada:
    case EstatusPago.pendiente:
    case EstatusPago.desconocido:
      return EstadoVisual.pendiente;
  }
}

/// Filtro por estado del historial (pestañas).
enum FiltroHistorial {
  todos,
  exitosos,
  rechazados,
  pendientes;

  /// Valor de `filtros.estatus` para el servidor; null si no se filtra ahí
  /// (o si el filtro agrupa varios estatus: ver [estatusesServidor]).
  String? get estatusServidor {
    switch (this) {
      case FiltroHistorial.todos:
      case FiltroHistorial.pendientes:
        return null;
      case FiltroHistorial.exitosos:
        return 'CONFIRMADA';
      case FiltroHistorial.rechazados:
        return 'RECHAZADA';
    }
  }

  /// Consultas independientes (cada una con su paginación) que componen el
  /// filtro. "Pendientes" agrupa PENDIENTE e INDETERMINADA, igual que la web.
  List<String?> get estatusesServidor => this == FiltroHistorial.pendientes
      ? const <String?>['PENDIENTE', 'INDETERMINADA']
      : <String?>[estatusServidor];

  /// Si [e] pertenece a este filtro (solo relevante al filtrar en cliente).
  bool acepta(ElementoHistorial e) {
    switch (this) {
      case FiltroHistorial.todos:
        return true;
      case FiltroHistorial.exitosos:
        return estadoVisualDe(e.estatus) == EstadoVisual.exitoso;
      case FiltroHistorial.rechazados:
        return estadoVisualDe(e.estatus) == EstadoVisual.rechazado;
      case FiltroHistorial.pendientes:
        return estadoVisualDe(e.estatus) == EstadoVisual.pendiente;
    }
  }
}

/// Etiqueta y valor del identificador de la operación: "Folio" si hay folio;
/// si no, "Número de operación" con `identificadorOperacion`. Null si no hay
/// ningún valor.
({String etiqueta, String valor})? identificadorDeOperacion(
  ElementoHistorial e,
) {
  final folio = (e.folio ?? '').trim();
  if (folio.isNotEmpty) return (etiqueta: 'Folio', valor: folio);
  final operacion = (e.identificadorOperacion ?? '').trim();
  if (operacion.isNotEmpty) {
    return (etiqueta: 'Número de operación', valor: operacion);
  }
  return null;
}

const List<String> _meses = [
  'ene',
  'feb',
  'mar',
  'abr',
  'may',
  'jun',
  'jul',
  'ago',
  'sep',
  'oct',
  'nov',
  'dic',
];

/// Fecha y hora al estilo es-MX hecho a mano: "13 nov 2026, 19:39". Null si el
/// texto no es una fecha válida.
String? formatearFechaHistorial(String? crudo) {
  if (crudo == null) return null;
  var fecha = DateTime.tryParse(crudo.trim());
  if (fecha == null) return null;
  if (fecha.isUtc) fecha = fecha.toLocal();
  String dos(int n) => n.toString().padLeft(2, '0');
  return '${fecha.day} ${_meses[fecha.month - 1]} ${fecha.year}, '
      '${dos(fecha.hour)}:${dos(fecha.minute)}';
}

/// Referencia o teléfono enmascarado de una operación (el que exista).
String? referenciaVisible(ElementoHistorial e) {
  final r = (e.referenciaEnmascarada ?? '').trim();
  if (r.isNotEmpty) return r;
  final t = (e.telefonoEnmascarado ?? '').trim();
  return t.isNotEmpty ? t : null;
}
