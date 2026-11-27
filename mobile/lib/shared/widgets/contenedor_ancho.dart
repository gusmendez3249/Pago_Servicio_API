import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';

/// Centra el contenido y limita su ancho al de lectura.
class ContenedorAncho extends StatelessWidget {
  const ContenedorAncho({
    super.key,
    required this.child,
    this.anchoMaximo = Medidas.anchoContenido,
  });

  final Widget child;
  final double anchoMaximo;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: anchoMaximo),
        child: child,
      ),
    );
  }
}
