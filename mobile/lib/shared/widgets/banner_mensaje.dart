import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';

enum TipoBanner { info, exito, error, aviso, neutro }

/// Contenedor de estado: fondo tenue del color del estado, radio 10 e icono.
/// El color nunca es la única señal: siempre hay icono y texto (en negro,
/// contraste AA).
class AvisoEstado extends StatelessWidget {
  const AvisoEstado({
    super.key,
    required this.tipo,
    required this.child,
    this.icono,
  });

  final TipoBanner tipo;
  final Widget child;

  /// Icono distinto del propio del tipo (p. ej. reloj en un pendiente).
  final IconData? icono;

  @override
  Widget build(BuildContext context) {
    final c = ColoresApp.de(context);
    final Color color;
    final IconData iconoTipo;
    switch (tipo) {
      case TipoBanner.error:
        color = c.error;
        iconoTipo = Icons.error_outline;
      case TipoBanner.exito:
        color = c.exito;
        iconoTipo = Icons.check_circle_outline;
      case TipoBanner.aviso:
        color = c.pendiente;
        iconoTipo = Icons.error_outline;
      case TipoBanner.info:
        color = c.info;
        iconoTipo = Icons.info_outline;
      case TipoBanner.neutro:
        color = c.info;
        iconoTipo = Icons.schedule;
    }
    // Fondo neutro con borde de 1 px, barra izquierda sólida de 3 px y el
    // icono en el color del estado; el texto siempre en el color principal.
    return Semantics(
      liveRegion: true,
      container: true,
      child: Container(
        decoration: BoxDecoration(
          color: c.superficie,
          borderRadius: BorderRadius.circular(Radios.campo),
          border: Border.all(color: c.linea),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(Radios.campo),
          child: IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(width: 3, color: color),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.all(Esp.x4),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(icono ?? iconoTipo, color: color, size: 22),
                        const SizedBox(width: Esp.x3),
                        Expanded(child: child),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Mensaje destacado con icono, usado para estados de éxito/error/aviso.
class BannerMensaje extends StatelessWidget {
  const BannerMensaje({
    super.key,
    required this.tipo,
    required this.mensaje,
    this.titulo,
    this.icono,
  });

  final TipoBanner tipo;
  final String mensaje;

  /// Primera línea en 600 (`.aviso__titulo`).
  final String? titulo;
  final IconData? icono;

  @override
  Widget build(BuildContext context) {
    final t = titulo;
    final cuerpo = Text(
      mensaje,
      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            color: ColoresApp.de(context).texto,
          ),
    );
    return AvisoEstado(
      tipo: tipo,
      icono: icono,
      child: t == null
          ? cuerpo
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(t, style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: Esp.x1),
                cuerpo,
              ],
            ),
    );
  }
}
