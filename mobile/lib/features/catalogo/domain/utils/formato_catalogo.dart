/// Formatea un importe en pesos mexicanos: `$1,234.50` (sin paquete `intl`).
String formatearMoneda(double valor) {
  final negativo = valor < 0;
  final partes = valor.abs().toStringAsFixed(2).split('.');
  final entero = partes[0];
  final agrupado = StringBuffer();
  for (var i = 0; i < entero.length; i++) {
    if (i > 0 && (entero.length - i) % 3 == 0) agrupado.write(',');
    agrupado.write(entero[i]);
  }
  final signo = negativo ? '-' : '';
  return '$signo\$${agrupado.toString()}.${partes[1]}';
}

/// Iniciales (máx. 2) de un nombre; `?` si no hay texto.
///
/// Con dos o más palabras usa la primera letra de las dos primeras; con una
/// sola palabra usa sus dos primeras letras.
String iniciales(String? nombre) {
  final palabras = (nombre ?? '')
      .trim()
      .split(RegExp(r'\s+'))
      .where((p) => p.isNotEmpty)
      .toList();
  if (palabras.isEmpty) return '?';
  if (palabras.length == 1) {
    final p = palabras.first;
    return (p.length >= 2 ? p.substring(0, 2) : p).toUpperCase();
  }
  return (palabras[0][0] + palabras[1][0]).toUpperCase();
}

const Map<String, String> _sinAcento = {
  'á': 'a',
  'à': 'a',
  'ä': 'a',
  'â': 'a',
  'ã': 'a',
  'é': 'e',
  'è': 'e',
  'ë': 'e',
  'ê': 'e',
  'í': 'i',
  'ì': 'i',
  'ï': 'i',
  'î': 'i',
  'ó': 'o',
  'ò': 'o',
  'ö': 'o',
  'ô': 'o',
  'õ': 'o',
  'ú': 'u',
  'ù': 'u',
  'ü': 'u',
  'û': 'u',
  'ñ': 'n',
};

/// Minúsculas y sin acentos, para comparar texto de forma tolerante.
String normalizarTexto(String? texto) {
  if (texto == null || texto.isEmpty) return '';
  final salida = StringBuffer();
  for (final c in texto.toLowerCase().split('')) {
    final unidad = c.codeUnitAt(0);
    // Ignora marcas diacríticas combinadas (U+0300 a U+036F).
    if (unidad >= 0x0300 && unidad <= 0x036F) continue;
    salida.write(_sinAcento[c] ?? c);
  }
  return salida.toString();
}
