import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../shared/widgets/estado_operacion.dart';
import '../../../catalogo/domain/utils/formato_catalogo.dart';
import '../../../catalogo/presentation/widgets/logo_servicio.dart';
import '../../domain/entities/elemento_historial.dart';
import '../../domain/utils/reglas_historial.dart';
import '../textos_historial.dart';

/// Texto del estado visual.
String textoEstadoVisual(EstadoVisual e) {
  switch (e) {
    case EstadoVisual.exitoso:
      return TextosHistorial.exitoso;
    case EstadoVisual.rechazado:
      return TextosHistorial.rechazado;
    case EstadoVisual.pendiente:
      return TextosHistorial.pendiente;
  }
}

/// Tipo de estado visual del chip.
TipoEstado tipoDeEstadoVisual(EstadoVisual e) {
  switch (e) {
    case EstadoVisual.exitoso:
      return TipoEstado.exito;
    case EstadoVisual.rechazado:
      return TipoEstado.error;
    case EstadoVisual.pendiente:
      return TipoEstado.pendiente;
  }
}

/// Fecha (día) y hora de una operación por separado: el día agrupa los
/// movimientos y la hora va en el renglón.
({String? dia, String? hora}) diaYHora(String? fechaTransaccion) {
  final completa = formatearFechaHistorial(fechaTransaccion);
  if (completa == null) return (dia: null, hora: null);
  final i = completa.lastIndexOf(', ');
  if (i < 0) return (dia: completa, hora: null);
  return (dia: completa.substring(0, i), hora: completa.substring(i + 2));
}

/// Renglón de un movimiento: logo, servicio y producto, hora, referencia y
/// folio a la izquierda; importe (alineado a la derecha) y chip de estado.
class FilaPago extends StatelessWidget {
  const FilaPago({super.key, required this.elemento, this.alTocar});

  final ElementoHistorial elemento;
  final VoidCallback? alTocar;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final colores = ColoresApp.de(context);
    final e = elemento;
    final servicio = (e.servicio ?? '').trim();
    final producto = (e.producto ?? '').trim();
    final fecha = formatearFechaHistorial(e.fechaTransaccion);
    final hora = diaYHora(e.fechaTransaccion).hora;
    final referencia = referenciaVisible(e);
    final operacion = identificadorDeOperacion(e);
    final visual = estadoVisualDe(e.estatus);
    final pequeno = estiloTexto(
      tamano: Escala.s13,
      color: tema.colorScheme.onSurfaceVariant,
      cifras: true,
    );
    final nombre = servicio.isEmpty ? 'Servicio sin nombre' : servicio;
    final etiqueta = [
      nombre,
      if (producto.isNotEmpty) producto,
      if (fecha != null) fecha,
      if (e.monto != null) formatearMoneda(e.monto!),
      textoEstadoVisual(visual),
    ].join(', ');

    return Semantics(
      button: alTocar != null,
      label: etiqueta,
      child: ExcludeSemantics(
        child: InkWell(
          onTap: alTocar,
          focusColor: colores.linea,
          highlightColor: colores.linea,
          splashColor: colores.linea,
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
                    url: e.logoUrl,
                    nombre: servicio,
                    tamano: Medidas.logoLista,
                  ),
                  const SizedBox(width: Esp.x4),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(nombre, style: tema.textTheme.titleMedium),
                        if (producto.isNotEmpty)
                          Text(
                            producto,
                            style: tema.textTheme.bodyMedium?.copyWith(
                              color: tema.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        // Meta en una línea que se parte (`.renglon__meta`).
                        Wrap(
                          spacing: Esp.x3,
                          children: [
                            if (hora != null) Text(hora, style: pequeno),
                            if (referencia != null)
                              Text(referencia, style: pequeno),
                            if (operacion != null)
                              Text(
                                '${operacion.etiqueta} ${operacion.valor}',
                                style: pequeno,
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: Esp.x4),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (e.monto != null)
                        Text(
                          formatearMoneda(e.monto!),
                          style: estiloTexto(
                            tamano: Escala.s16,
                            peso: FontWeight.w600,
                            color: tema.colorScheme.onSurface,
                            cifras: true,
                          ),
                        ),
                      const SizedBox(height: Esp.x1),
                      ChipEstado(
                        tipo: tipoDeEstadoVisual(visual),
                        texto: textoEstadoVisual(visual),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Etiqueta de cada filtro por estado.
String etiquetaFiltro(FiltroHistorial f) {
  switch (f) {
    case FiltroHistorial.todos:
      return TextosHistorial.todos;
    case FiltroHistorial.exitosos:
      return TextosHistorial.exitosos;
    case FiltroHistorial.rechazados:
      return TextosHistorial.rechazados;
    case FiltroHistorial.pendientes:
      return TextosHistorial.pendientes;
  }
}
