import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';
import 'logo_servicio.dart';

/// Cabecera de las pantallas del servicio y del flujo de pago, igual que la
/// franja de la web (`.franja__servicio` y `.franja__dato`): logo de 64, el
/// título serif de la pantalla con una línea secundaria y, opcional, el dato
/// principal (etiqueta e importe de 40).
class CabeceraServicio extends StatelessWidget {
  const CabeceraServicio({
    super.key,
    required this.logoUrl,
    required this.nombreLogo,
    required this.titulo,
    this.subtitulo,
    this.etiquetaDato,
    this.dato,
  });

  final String? logoUrl;

  /// Nombre del servicio (iniciales del logo de respaldo).
  final String nombreLogo;

  /// Título serif de la pantalla.
  final String titulo;
  final String? subtitulo;

  final String? etiquetaDato;
  final String? dato;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final sub = subtitulo?.trim() ?? '';
    final d = dato;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            LogoServicio(
              url: logoUrl,
              nombre: nombreLogo,
              tamano: Esp.x16,
            ),
            const SizedBox(width: Esp.x4),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Semantics(
                    header: true,
                    child: Text(
                      titulo,
                      style: estiloTitular(
                        tamano: Escala.s28,
                        color: colores.texto,
                      ),
                    ),
                  ),
                  if (sub.isNotEmpty) ...[
                    const SizedBox(height: Esp.x1),
                    Text(
                      sub,
                      style: estiloTexto(
                        tamano: Escala.s14,
                        color: colores.textoSecundario,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
        if (d != null) ...[
          const SizedBox(height: Esp.x6),
          if (etiquetaDato != null)
            Padding(
              padding: const EdgeInsets.only(bottom: Esp.x1),
              child: Text(
                etiquetaDato!,
                style: estiloTexto(
                  tamano: Escala.s14,
                  color: colores.textoSecundario,
                ),
              ),
            ),
          Text(
            d,
            style: estiloImporte(tamano: Escala.s40, color: colores.texto),
          ),
        ],
      ],
    );
  }
}

/// Solo el título serif de la franja (estados de carga, error o sin datos).
class TituloFranja extends StatelessWidget {
  const TituloFranja(this.texto, {super.key, this.subtitulo});

  final String texto;
  final String? subtitulo;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final sub = subtitulo;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Semantics(
          header: true,
          child: Text(
            texto,
            style: estiloTitular(tamano: Escala.s28, color: colores.texto),
          ),
        ),
        if (sub != null && sub.trim().isNotEmpty) ...[
          const SizedBox(height: Esp.x1),
          Text(
            sub,
            style: estiloTexto(
              tamano: Escala.s14,
              color: colores.textoSecundario,
            ),
          ),
        ],
      ],
    );
  }
}
