import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';

/// Enlace (`.enlace` de la web): texto principal en 600, subrayado, con
/// objetivo táctil de 48.
class Enlace extends StatelessWidget {
  const Enlace({super.key, required this.texto, required this.alTocar});

  final String texto;
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: TextButton(
        style: TextButton.styleFrom(
          padding: EdgeInsets.zero,
          textStyle: estiloTexto(
            tamano: Escala.s16,
            peso: FontWeight.w600,
            decoracion: TextDecoration.underline,
          ),
        ),
        onPressed: alTocar,
        child: Text(texto),
      ),
    );
  }
}
