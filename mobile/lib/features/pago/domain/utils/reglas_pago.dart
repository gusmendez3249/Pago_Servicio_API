/// Reglas puras del flujo de pago (sin Flutter ni red).
library;

/// Tipo de front de los productos de recarga/compra con teléfono.
const int kTipoFrontTelefono = 1;

/// Importe mínimo y máximo (en centavos) cuando se captura el del recibo.
const int kImporteMinimoCentavos = 100;
const int kImporteMaximoCentavos = 1000000;

/// Intervalos del sondeo de estado (desde que queda pendiente).
const List<Duration> kIntervalosSondeo = [
  Duration(seconds: 10),
  Duration(seconds: 20),
  Duration(seconds: 35),
  Duration(seconds: 50),
  Duration(seconds: 70),
  Duration(seconds: 90),
  Duration(seconds: 120),
];

/// Consultas por llave con 404 seguidas tras las cuales se da por no llegado.
const int kMaximo404Seguidos = 3;

/// Tiempo de espera de la respuesta del pago.
const Duration kTiempoEsperaPago = Duration(seconds: 75);

enum ErrorTelefono { formato, noCoincide }

enum ErrorImporte { vacio, fueraDeRango }

final RegExp _soloDigitos10 = RegExp(r'^\d{10}$');
final RegExp _importe = RegExp(r'^(\d{1,7})(?:\.(\d{1,2}))?$');
final RegExp _pin = RegExp(r'^\d{4}$');

bool pinValido(String pin) => _pin.hasMatch(pin);

/// Valida el teléfono y su confirmación. Null si todo está bien.
ErrorTelefono? validarTelefono(String telefono, String confirmacion) {
  final t = telefono.trim();
  final c = confirmacion.trim();
  if (!_soloDigitos10.hasMatch(t) || !_soloDigitos10.hasMatch(c)) {
    return ErrorTelefono.formato;
  }
  if (t != c) return ErrorTelefono.noCoincide;
  return null;
}

/// Importe en centavos, o null si el texto no es un importe válido de hasta
/// dos decimales. No evalúa el rango.
int? centavosDeTexto(String texto) {
  final m = _importe.firstMatch(texto.trim());
  if (m == null) return null;
  final enteros = int.parse(m.group(1)!);
  final decimales = (m.group(2) ?? '').padRight(2, '0');
  return enteros * 100 + (decimales.isEmpty ? 0 : int.parse(decimales));
}

/// Resultado de validar el importe capturado.
class ResultadoImporte {
  const ResultadoImporte({this.monto, this.error});

  final double? monto;
  final ErrorImporte? error;
}

ResultadoImporte validarImporte(String texto) {
  if (texto.trim().isEmpty) {
    return const ResultadoImporte(error: ErrorImporte.vacio);
  }
  final centavos = centavosDeTexto(texto);
  if (centavos == null ||
      centavos < kImporteMinimoCentavos ||
      centavos > kImporteMaximoCentavos) {
    return const ResultadoImporte(error: ErrorImporte.fueraDeRango);
  }
  return ResultadoImporte(monto: centavos / 100);
}

/// Convierte pesos a centavos enteros (evita errores de punto flotante).
int aCentavos(double pesos) => (pesos * 100).round();

/// Cifras del resumen de pago.
class ResumenPago {
  const ResumenPago({
    required this.total,
    required this.saldo,
    required this.saldoDespues,
    required this.saldoSuficiente,
  });

  final double total;
  final double saldo;
  final double saldoDespues;
  final bool saldoSuficiente;
}

/// La comisión no se cobra al usuario: el total es el monto.
ResumenPago calcularResumen({required double total, required double saldo}) {
  final centavosSaldo = aCentavos(saldo);
  final centavosTotal = aCentavos(total);
  return ResumenPago(
    total: total,
    saldo: saldo,
    saldoDespues: (centavosSaldo - centavosTotal) / 100,
    saldoSuficiente: centavosSaldo >= centavosTotal,
  );
}

/// Esperas entre consultas a partir de los instantes acumulados (10, 20, 35…).
List<Duration> esperasEntreConsultas(List<Duration> instantes) {
  final esperas = <Duration>[];
  var previo = Duration.zero;
  for (final instante in instantes) {
    final espera = instante - previo;
    esperas.add(espera.isNegative ? Duration.zero : espera);
    previo = instante;
  }
  return esperas;
}
