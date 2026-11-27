import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';

/// Indicador de tres pasos del flujo de pago ("Servicio", "Referencia" o
/// "Teléfono", y "Confirmación"): texto "Paso 2 de 3" con el nombre del paso
/// y una barra segmentada. Vive dentro de la cabecera negra.
class IndicadorPasos extends StatelessWidget {
  const IndicadorPasos({
    super.key,
    required this.actual,
    this.segundo = 'Referencia',
    this.etiquetas,
  });

  /// Paso en curso, desde 1.
  final int actual;

  /// Nombres de todos los pasos; sin ellos se usan los del flujo de pago.
  final List<String>? etiquetas;

  /// Etiqueta del segundo paso.
  final String segundo;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final etiquetas = this.etiquetas ?? ['Servicio', segundo, 'Confirmación'];
    final total = etiquetas.length;
    final etiqueta = etiquetas[actual - 1];
    return Semantics(
      label: 'Paso $actual de $total: $etiqueta',
      child: ExcludeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: 'Paso $actual de $total  ',
                    style: estiloTexto(
                      tamano: Escala.s13,
                      peso: FontWeight.w500,
                      color: colores.textoSecundario,
                      cifras: true,
                    ),
                  ),
                  TextSpan(
                    text: etiqueta,
                    style: estiloTexto(
                      tamano: Escala.s13,
                      peso: FontWeight.w600,
                      color: colores.texto,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: Esp.x2),
            Row(
              children: [
                for (var i = 1; i <= total; i++) ...[
                  if (i > 1) const SizedBox(width: Esp.x1),
                  Expanded(
                    child: Container(
                      height: 2,
                      color: i <= actual ? colores.marca : colores.lineaFuerte,
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}
