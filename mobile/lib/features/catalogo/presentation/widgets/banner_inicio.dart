import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../core/router/app_router.dart';
import '../../../../shared/widgets/banner_imagen.dart';
import '../../../../shared/widgets/enlace.dart';
import '../../../../shared/widgets/ficha_datos.dart';

/// Bloque "Para ti" del inicio, igual que la web (después de la lista):
/// banner 16:7 de alto máximo 200 con radio 16 y pie sobre la foto, y una
/// tarjeta con "Comprobante al momento" y el enlace "Ver mis pagos".
class BannerInicio extends StatelessWidget {
  const BannerInicio({super.key});

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final tema = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const TituloSeccion('Para ti'),
        // Foto arriba y texto debajo, sobre tarjeta blanca: nunca texto
        // encima de la foto.
        DecoratedBox(
          decoration: BoxDecoration(
            color: colores.superficie,
            border: Border.all(color: colores.linea),
            borderRadius: BorderRadius.circular(Radios.hoja),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(Radios.hoja),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const ImagenBanner(
                  asset: FotosBanner.inicio,
                  semantica: 'Dos personas sonríen en un sillón mientras '
                      'usan una laptop',
                  alineacion: Alignment(0, -0.4),
                  altoMaximo: 200,
                ),
                Padding(
                  padding: const EdgeInsets.all(Esp.x4),
                  child: Text(
                    'Paga tus servicios sin filas',
                    style: tema.textTheme.titleMedium,
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: Esp.x4),
        TarjetaDatos(
          relleno: const EdgeInsets.fromLTRB(Esp.x4, Esp.x4, Esp.x4, 0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Comprobante al momento', style: tema.textTheme.titleMedium),
              const SizedBox(height: Esp.x1),
              Text(
                'Cada pago genera un folio que puedes consultar cuando lo '
                'necesites.',
                style: tema.textTheme.bodySmall,
              ),
              const SizedBox(height: Esp.x2),
              Enlace(
                texto: 'Ver mis pagos',
                alTocar: () => context.go(Rutas.historial),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
