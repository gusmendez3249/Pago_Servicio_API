import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';
import 'app_scaffold.dart';
import 'contenedor_ancho.dart';
import 'indicador_pasos.dart';

/// Página con cabecera negra (con el indicador de pasos opcional) y
/// contenido con desplazamiento; el [pie] va anclado abajo.
class PaginaFlujo extends StatelessWidget {
  const PaginaFlujo({
    super.key,
    required this.children,
    this.titulo,
    this.tituloDiscreto = false,
    this.marca = false,
    this.cabecera,
    this.pie,
    this.paso,
    this.segundoPaso = 'Referencia',
    this.cancelable = true,
  });

  final List<Widget> children;

  /// Título de la barra superior.
  final String? titulo;

  /// Ver [MarcoBanda.tituloDiscreto].
  final bool tituloDiscreto;

  /// Logotipo en la barra superior en lugar del título.
  final bool marca;

  /// Dato o acción principal sobre la franja negra.
  final Widget? cabecera;

  /// Botón primario anclado al pie.
  final Widget? pie;

  /// Paso en curso (1 a 3); null oculta el indicador.
  final int? paso;
  final String segundoPaso;

  /// En el flujo (con [paso]) muestra "Cancelar", que vuelve al inicio;
  /// falso mientras el pago está en proceso (igual que la web).
  final bool cancelable;

  @override
  Widget build(BuildContext context) {
    final abajo = pie == null ? MediaQuery.paddingOf(context).bottom : 0.0;
    return MarcoBanda(
      cancelar: paso != null && cancelable,
      titulo: titulo,
      tituloDiscreto: tituloDiscreto,
      marca: marca,
      pasos: paso == null
          ? null
          : IndicadorPasos(actual: paso!, segundo: segundoPaso),
      cabecera: cabecera,
      pie: pie,
      cuerpo: SingleChildScrollView(
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        padding: EdgeInsets.fromLTRB(
          Esp.x4,
          Esp.x6,
          Esp.x4,
          Esp.x8 + abajo,
        ),
        child: ContenedorAncho(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              ...children,
            ],
          ),
        ),
      ),
    );
  }
}
