import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/config/app_theme.dart';
import '../providers/auth_providers.dart';

/// "Cerrar sesión" (botón secundario) que abre un diálogo de confirmación,
/// igual que la web. Solo al confirmar se cierra la sesión; tocar fuera, el
/// botón atrás o "Cancelar" lo descartan. El caso de uso no cambia: limpia la
/// sesión local pase lo que pase.
class BotonCerrarSesion extends StatelessWidget {
  const BotonCerrarSesion({super.key});

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: OutlinedButton.icon(
        onPressed: () => mostrarConfirmacionCerrarSesion(context),
        icon: const Icon(Icons.logout),
        label: const Text('Cerrar sesión'),
      ),
    );
  }
}

/// Abre el diálogo de confirmación de cierre de sesión.
Future<void> mostrarConfirmacionCerrarSesion(BuildContext context) {
  return showDialog<void>(
    context: context,
    barrierDismissible: true,
    barrierColor: Paleta.velo,
    barrierLabel: 'Cerrar el diálogo',
    builder: (_) => const DialogoCerrarSesion(),
  );
}

/// Diálogo centrado: título serif, texto y dos botones de 52 ("Cancelar"
/// secundario y "Cerrar sesión" en rojo). En fila si caben; si no, apilados
/// con el rojo arriba.
class DialogoCerrarSesion extends ConsumerStatefulWidget {
  const DialogoCerrarSesion({super.key});

  @override
  ConsumerState<DialogoCerrarSesion> createState() =>
      _DialogoCerrarSesionState();
}

class _DialogoCerrarSesionState extends ConsumerState<DialogoCerrarSesion> {
  bool _cerrando = false;

  Future<void> _cerrar() async {
    if (_cerrando) return;
    setState(() => _cerrando = true);
    await ref.read(cerrarSesionProvider)();
    if (mounted) Navigator.of(context).maybePop();
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final colores = ColoresApp.de(context);
    const alto = Size(64, 52);

    final cancelar = OutlinedButton(
      style: OutlinedButton.styleFrom(minimumSize: alto),
      onPressed: _cerrando ? null : () => Navigator.of(context).pop(),
      child: const Text('Cancelar'),
    );
    final confirmar = FilledButton(
      style: ButtonStyle(
        minimumSize: const WidgetStatePropertyAll(alto),
        // Rojo plano, sin filo metálico: solo para esta acción destructiva.
        backgroundBuilder: (context, estados, hijo) => hijo ?? const SizedBox(),
        backgroundColor: WidgetStateProperty.resolveWith(
          (e) => e.contains(WidgetState.pressed) ||
                  e.contains(WidgetState.hovered) ||
                  e.contains(WidgetState.disabled)
              ? Paleta.peligroFuerte
              : Paleta.peligro,
        ),
        foregroundColor: const WidgetStatePropertyAll(Paleta.blanco),
      ),
      onPressed: _cerrando ? null : _cerrar,
      child: _cerrando
          ? const SizedBox(
              width: Medidas.giro,
              height: Medidas.giro,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: Paleta.blanco,
                semanticsLabel: 'Cerrando sesión',
              ),
            )
          : const Text('Cerrar sesión'),
    );

    return PopScope(
      canPop: !_cerrando,
      child: Dialog(
        insetPadding: const EdgeInsets.all(Esp.x4),
        backgroundColor: colores.superficie,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(Radios.tarjeta),
          side: BorderSide(color: colores.linea),
        ),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 400),
          child: Semantics(
            scopesRoute: true,
            namesRoute: true,
            explicitChildNodes: true,
            label: 'Confirmar cierre de sesión',
            child: Padding(
              padding: const EdgeInsets.all(Esp.x6),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Semantics(
                    header: true,
                    child: Text(
                      '¿Cerrar sesión?',
                      style: estiloTitular(
                        tamano: Escala.s28,
                        color: colores.texto,
                      ),
                    ),
                  ),
                  const SizedBox(height: Esp.x3),
                  Text(
                    'Tendrás que volver a iniciar sesión para pagar tus '
                    'servicios.',
                    style: tema.textTheme.bodyLarge?.copyWith(
                      color: colores.textoSecundario,
                    ),
                  ),
                  const SizedBox(height: Esp.x6),
                  LayoutBuilder(
                    builder: (context, r) {
                      // En fila si caben sin partir el texto (dos botones
                      // de al menos 160); si no, apilados con el rojo arriba.
                      if (r.maxWidth >= 2 * 160 + Esp.x3 &&
                          MediaQuery.textScalerOf(context).scale(16) <= 16) {
                        return Row(
                          children: [
                            Expanded(child: cancelar),
                            const SizedBox(width: Esp.x3),
                            Expanded(child: confirmar),
                          ],
                        );
                      }
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          confirmar,
                          const SizedBox(height: Esp.x3),
                          cancelar,
                        ],
                      );
                    },
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
