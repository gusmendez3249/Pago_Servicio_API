import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';
import '../../domain/entities/producto.dart';
import '../../domain/utils/formato_catalogo.dart';
import 'logo_servicio.dart';

/// Nombre del servicio listo para mostrar.
String nombreServicio(Producto p) {
  final s = p.servicio?.trim() ?? '';
  return s.isEmpty ? 'Servicio sin nombre' : s;
}

/// Renglón de servicio (`app-servicio-card` de la web): logo circular con
/// iniciales en el color de la categoría, nombre en 600, producto y
/// categoría; a la derecha la etiqueta del importe sobre el monto, y chevron.
class FilaProducto extends StatelessWidget {
  const FilaProducto({super.key, required this.producto, this.alTocar});

  final Producto producto;
  final VoidCallback? alTocar;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final colores = ColoresApp.de(context);
    final nombreProducto = producto.producto?.trim() ?? '';
    final s = producto.servicio?.trim() ?? '';
    final etiqueta = '${s.isEmpty ? 'Servicio' : s}'
        '${nombreProducto.isEmpty ? '' : ', $nombreProducto'}'
        ', ${producto.categoriaNombre}';
    final precio = producto.precio;
    final secundario = tema.textTheme.bodyMedium?.copyWith(
      color: colores.textoSecundario,
    );
    final meta = estiloTexto(
      tamano: Escala.s13,
      color: colores.textoSecundario,
    );

    return Semantics(
      button: alTocar != null,
      label: etiqueta,
      child: ExcludeSemantics(
        child: InkWell(
          onTap: alTocar,
          focusColor: colores.superficie,
          highlightColor: colores.superficie,
          splashColor: colores.superficie,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 72),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: Esp.x4,
                vertical: Esp.x3,
              ),
              child: Row(
                children: [
                  LogoServicio(
                    url: producto.logoUrl,
                    nombre: producto.servicio,
                    tamano: Medidas.logoLista,
                  ),
                  const SizedBox(width: Esp.x4),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          s.isEmpty ? 'Servicio' : s,
                          style: tema.textTheme.titleMedium,
                        ),
                        if (nombreProducto.isNotEmpty)
                          Text(nombreProducto, style: secundario),
                        Text(producto.categoriaNombre, style: meta),
                      ],
                    ),
                  ),
                  if (precio != null) ...[
                    const SizedBox(width: Esp.x4),
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(producto.etiquetaImporte, style: meta),
                        const SizedBox(height: Esp.x1),
                        Text(
                          formatearMoneda(precio),
                          style: estiloTexto(
                            tamano: Escala.s16,
                            peso: FontWeight.w600,
                            color: colores.texto,
                            cifras: true,
                          ),
                        ),
                      ],
                    ),
                  ],
                  if (alTocar != null) ...[
                    const SizedBox(width: Esp.x4),
                    Icon(Icons.chevron_right, color: colores.textoSecundario),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
