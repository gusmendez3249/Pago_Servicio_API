import 'package:flutter/material.dart';

import '../../domain/utils/formato_catalogo.dart';

/// Icono de una categoría (solo presentación; el dominio no cambia). Único
/// mapa por nombre de categoría normalizado; sin colores por categoría.

/// Mismo mapa que la web (`categoria-visual.ts`). El orden importa:
/// "recargas" contiene "gas", por eso va antes.
IconData iconoCategoria(String? nombre) {
  final n = normalizarTexto(nombre);
  bool tiene(List<String> claves) => claves.any(n.contains);
  if (tiene(['luz', 'electric', 'energia'])) {
    return Icons.bolt_outlined;
  }
  if (tiene(['agua'])) {
    return Icons.water_drop_outlined;
  }
  if (tiene(
      ['recarga', 'celular', 'tiempo aire', 'movil', 'tarjetas de regalo'])) {
    return Icons.smartphone_outlined;
  }
  if (RegExp(r'(^|\s)gas(\s|$)').hasMatch(n)) {
    return Icons.local_fire_department_outlined;
  }
  if (tiene(['telefon', 'fija'])) {
    return Icons.call_outlined;
  }
  if (tiene(['internet', 'cable', 'tv', 'television', 'streaming'])) {
    return Icons.wifi_outlined;
  }
  if (tiene(['envio', 'dinero', 'transferencia'])) {
    return Icons.send_outlined;
  }
  if (tiene(['consulta'])) {
    return Icons.search;
  }
  if (tiene(['otros'])) {
    return Icons.add;
  }
  return Icons.receipt_long_outlined;
}
