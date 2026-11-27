import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';

/// Estado de una sección, igual que la web.
///
/// - Error (por omisión, `.estado-error`): sin caja; icono de alerta en un
///   círculo de 48, mensaje en 600 y la acción ("Reintentar") a la izquierda.
/// - Vacío ([vacio], `.mensaje`): tarjeta con icono en círculo, mensaje y una
///   acción.
class MensajeEstado extends StatelessWidget {
  const MensajeEstado({
    super.key,
    required this.icono,
    required this.titulo,
    this.detalle,
    this.etiquetaAccion,
    this.alAccionar,
    this.margen = Esp.margen,
    this.vacio = false,
    this.accionSecundaria = false,
  });

  final IconData icono;
  final String titulo;
  final String? detalle;
  final String? etiquetaAccion;
  final VoidCallback? alAccionar;

  /// Margen lateral; 0 dentro de una página que ya lo aplica.
  final double margen;

  /// Estado vacío (tarjeta) en lugar de error.
  final bool vacio;

  /// La acción va como botón secundario (borde de 1 px).
  final bool accionSecundaria;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final colores = ColoresApp.de(context);
    final accion = (etiquetaAccion != null && alAccionar != null)
        ? (accionSecundaria
            ? OutlinedButton(
                onPressed: alAccionar, child: Text(etiquetaAccion!))
            : FilledButton(onPressed: alAccionar, child: Text(etiquetaAccion!)))
        : null;
    final circulo = ExcludeSemantics(
      child: Container(
        width: Esp.x12,
        height: Esp.x12,
        decoration: BoxDecoration(
          color: colores.circulo,
          shape: BoxShape.circle,
        ),
        child: Icon(
          icono,
          color: vacio ? colores.texto : colores.error,
        ),
      ),
    );
    final d = detalle;

    final Widget contenido;
    if (vacio) {
      contenido = Container(
        padding: const EdgeInsets.all(Esp.x6),
        decoration: BoxDecoration(
          color: colores.superficie,
          border: Border.all(color: colores.linea),
          borderRadius: BorderRadius.circular(Radios.tarjeta),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            circulo,
            const SizedBox(height: Esp.x3),
            Text(titulo, style: tema.textTheme.bodyLarge),
            if (d != null) ...[
              const SizedBox(height: Esp.x1),
              Text(d, style: tema.textTheme.bodySmall),
            ],
            if (accion != null) ...[
              const SizedBox(height: Esp.x6),
              accion,
            ],
          ],
        ),
      );
    } else {
      contenido = Padding(
        padding: const EdgeInsets.symmetric(vertical: Esp.x4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            circulo,
            const SizedBox(height: Esp.x4),
            Text(
              titulo,
              style: tema.textTheme.bodyLarge?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
            if (d != null) ...[
              const SizedBox(height: Esp.x1),
              Text(d, style: tema.textTheme.bodySmall),
            ],
            if (accion != null) ...[
              const SizedBox(height: Esp.x4),
              accion,
            ],
          ],
        ),
      );
    }

    return Align(
      alignment: Alignment.topLeft,
      child: Padding(
        padding: EdgeInsets.fromLTRB(margen, Esp.x2, margen, Esp.x2),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: Semantics(liveRegion: true, child: contenido),
        ),
      ),
    );
  }
}

/// Línea de carga: indicador y texto secundario (`.cargando` de la web).
class LineaCargando extends StatelessWidget {
  const LineaCargando({super.key, required this.texto});

  final String texto;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    return Semantics(
      liveRegion: true,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: Esp.x6),
        child: Row(
          children: [
            const SizedBox(
              width: Medidas.giro,
              height: Medidas.giro,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            const SizedBox(width: Esp.x3),
            Expanded(
              child: Text(
                texto,
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: colores.textoSecundario,
                    ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Renglón esqueleto estático (sin brillo animado) mientras carga una lista:
/// círculo de 44 y dos líneas (`.esqueleto` de la web).
class EsqueletoTarjeta extends StatelessWidget {
  const EsqueletoTarjeta({super.key, this.conLogo = true});

  final bool conLogo;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);

    Widget barra(double fraccion) => FractionallySizedBox(
          alignment: Alignment.centerLeft,
          widthFactor: fraccion,
          child: Container(
            height: Esp.x3,
            decoration: BoxDecoration(
              color: colores.linea,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        );

    return ExcludeSemantics(
      child: Container(
        constraints: const BoxConstraints(minHeight: 72),
        padding: const EdgeInsets.symmetric(
          horizontal: Esp.x4,
          vertical: Esp.x3,
        ),
        child: Row(
          children: [
            if (conLogo) ...[
              Container(
                width: Medidas.logoLista,
                height: Medidas.logoLista,
                decoration: BoxDecoration(
                  color: colores.linea,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: Esp.x4),
            ],
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  barra(0.45),
                  const SizedBox(height: Esp.x2),
                  barra(0.7),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Anuncio accesible de carga para listas con esqueletos.
class AnuncioCarga extends StatelessWidget {
  const AnuncioCarga({super.key, required this.texto});

  final String texto;

  @override
  Widget build(BuildContext context) {
    return Semantics(liveRegion: true, label: texto, child: const SizedBox());
  }
}
