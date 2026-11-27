import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';
import '../../domain/entities/producto.dart';
import '../../domain/utils/formato_catalogo.dart';

/// Importe del producto con su etiqueta ("Comisión" o "Precio"), en cifras
/// tabulares. No muestra nada si el precio es nulo.
///
/// [grande] es la versión de la franja negra: etiqueta arriba e importe de
/// 40 en el color principal.
class ImporteProducto extends StatelessWidget {
  const ImporteProducto(
      {super.key, required this.producto, this.grande = false});

  final Producto producto;
  final bool grande;

  @override
  Widget build(BuildContext context) {
    final precio = producto.precio;
    if (precio == null) return const SizedBox.shrink();
    final tema = Theme.of(context);
    final colores = ColoresApp.de(context);
    final monto = formatearMoneda(precio);
    final estiloMonto = grande
        ? estiloImporte(tamano: Escala.s40, color: colores.texto)
        : estiloImporte(
            tamano: Escala.s16,
            peso: FontWeight.w600,
            color: tema.colorScheme.onSurface,
          );
    final estiloEtiqueta = grande
        ? estiloTexto(
            tamano: Escala.s14,
            peso: FontWeight.w500,
            color: colores.textoSecundario,
          )
        : tema.textTheme.bodySmall;
    return Semantics(
      label: '${producto.etiquetaImporte}: $monto pesos',
      child: ExcludeSemantics(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment:
              grande ? CrossAxisAlignment.start : CrossAxisAlignment.end,
          children: [
            Text(producto.etiquetaImporte, style: estiloEtiqueta),
            if (grande) const SizedBox(height: Esp.x1),
            Text(monto, style: estiloMonto),
          ],
        ),
      ),
    );
  }
}
