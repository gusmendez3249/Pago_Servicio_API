import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';

/// Estado de una operación tal como se muestra.
enum TipoEstado { exito, pendiente, error }

extension TipoEstadoVista on TipoEstado {
  /// Iconos de la web: check, reloj y alerta.
  IconData get icono {
    switch (this) {
      case TipoEstado.exito:
        return Icons.check_rounded;
      case TipoEstado.pendiente:
        return Icons.schedule_rounded;
      case TipoEstado.error:
        return Icons.error_outline_rounded;
    }
  }

  Color color(ColoresApp c) {
    switch (this) {
      case TipoEstado.exito:
        return c.exito;
      case TipoEstado.pendiente:
        return c.pendiente;
      case TipoEstado.error:
        return c.error;
    }
  }
}

/// Icono de estado sobre el bloque negro del comprobante y del resultado:
/// círculo sólido del color del estado con el icono en blanco.
class IconoEstado extends StatelessWidget {
  const IconoEstado({super.key, required this.tipo, this.tamano = 56});

  final TipoEstado tipo;
  final double tamano;

  @override
  Widget build(BuildContext context) {
    const c = ColoresApp.claro;
    return ExcludeSemantics(
      child: Container(
        width: tamano,
        height: tamano,
        decoration: BoxDecoration(
          color: tipo.color(c),
          shape: BoxShape.circle,
        ),
        child: Icon(tipo.icono, color: Paleta.blanco, size: tamano / 2),
      ),
    );
  }
}

/// Etiqueta de estado de un pago: fondo de tarjeta con borde de 1 px, icono
/// pequeño en el color del estado y texto principal.
class ChipEstado extends StatelessWidget {
  const ChipEstado({super.key, required this.tipo, required this.texto});

  final TipoEstado tipo;
  final String texto;

  @override
  Widget build(BuildContext context) {
    final c = ColoresApp.de(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: Esp.x2, vertical: Esp.x1),
      decoration: BoxDecoration(
        color: c.superficie,
        border: Border.all(color: c.linea),
        borderRadius: BorderRadius.circular(Radios.campo),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(tipo.icono, size: 14, color: tipo.color(c)),
          const SizedBox(width: Esp.x1),
          Flexible(
            child: Text(
              texto,
              style: estiloTexto(
                tamano: Escala.s13,
                peso: FontWeight.w600,
                color: c.texto,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
