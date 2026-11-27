import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/config/app_theme.dart';
import '../../core/router/app_router.dart';

/// Destinos de la barra inferior.
enum SeccionApp { inicio, pagos, cuenta }

/// Barra inferior con los tres destinos principales. Icono y etiqueta siempre
/// visibles; el activo lleva icono relleno, etiqueta en negrita y una línea
/// negra arriba.
class BarraSecciones extends StatelessWidget {
  const BarraSecciones({super.key, required this.actual});

  final SeccionApp actual;

  void _ir(BuildContext context, SeccionApp s) {
    if (s == actual) return;
    context.go(switch (s) {
      SeccionApp.inicio => Rutas.catalogo,
      SeccionApp.pagos => Rutas.historial,
      SeccionApp.cuenta => Rutas.cuenta,
    });
  }

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    return DecoratedBox(
      // Barra inferior blanca con borde superior.
      decoration: BoxDecoration(
        color: colores.superficie,
        border: Border(top: BorderSide(color: colores.linea)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            _Seccion(
              etiqueta: 'Inicio',
              icono: Icons.home_outlined,
              iconoActivo: Icons.home_rounded,
              activa: actual == SeccionApp.inicio,
              alTocar: () => _ir(context, SeccionApp.inicio),
            ),
            _Seccion(
              etiqueta: 'Mis pagos',
              icono: Icons.receipt_long_outlined,
              iconoActivo: Icons.receipt_long,
              activa: actual == SeccionApp.pagos,
              alTocar: () => _ir(context, SeccionApp.pagos),
            ),
            _Seccion(
              etiqueta: 'Cuenta',
              icono: Icons.person_outline,
              iconoActivo: Icons.person,
              activa: actual == SeccionApp.cuenta,
              alTocar: () => _ir(context, SeccionApp.cuenta),
            ),
          ],
        ),
      ),
    );
  }
}

class _Seccion extends StatelessWidget {
  const _Seccion({
    required this.etiqueta,
    required this.icono,
    required this.iconoActivo,
    required this.activa,
    required this.alTocar,
  });

  final String etiqueta;
  final IconData icono;
  final IconData iconoActivo;
  final bool activa;
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final color = activa ? colores.texto : colores.textoSecundario;
    return Expanded(
      child: Semantics(
        button: true,
        selected: activa,
        label: etiqueta,
        child: ExcludeSemantics(
          child: InkWell(
            onTap: alTocar,
            focusColor: colores.linea,
            highlightColor: colores.linea,
            splashColor: colores.linea,
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 64),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: Esp.x12,
                    height: Medidas.indicador,
                    color: activa ? colores.marca : Paleta.transparente,
                  ),
                  const SizedBox(height: Esp.x2),
                  Icon(activa ? iconoActivo : icono, color: color),
                  const SizedBox(height: Esp.x1),
                  Text(
                    etiqueta,
                    style: estiloTexto(
                      tamano: Escala.s13,
                      peso: activa ? FontWeight.w600 : FontWeight.w500,
                      color: color,
                    ),
                  ),
                  const SizedBox(height: Esp.x2),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
