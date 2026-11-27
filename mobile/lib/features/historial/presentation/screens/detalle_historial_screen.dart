import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/router/app_router.dart';
import '../../../../shared/widgets/app_scaffold.dart';
import '../../../../shared/widgets/pagina_flujo.dart';
import '../../../catalogo/presentation/widgets/cabecera_servicio.dart';
import '../../../catalogo/presentation/widgets/estados_catalogo.dart';
import '../../../pago/presentation/widgets/comprobante_vista.dart';
import '../../../pago/presentation/widgets/resultado_vista.dart';
import '../providers/detalle_historial_notifier.dart';
import '../providers/historial_providers.dart';
import '../textos_historial.dart';

/// Detalle de una operación del historial: el mismo comprobante del flujo de
/// pago.
class DetalleHistorialScreen extends ConsumerWidget {
  const DetalleHistorialScreen({super.key, required this.upc});

  final String? upc;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    void volver() => context.go(Rutas.historial);
    final clave = (upc ?? '').trim();

    Widget cuerpo;
    if (clave.isEmpty) {
      cuerpo = _noEncontrada(volver);
    } else {
      final estado = ref.watch(detalleHistorialProvider(clave));
      final notificador = ref.read(detalleHistorialProvider(clave).notifier);
      switch (estado.fase) {
        case FaseDetalle.cargando:
          cuerpo = const PaginaFlujo(
            titulo: TextosHistorial.titulo,
            tituloDiscreto: true,
            cabecera: TituloFranja(TextosHistorial.tituloDetalle),
            children: [LineaCargando(texto: 'Cargando la operación…')],
          );
        case FaseDetalle.noEncontrado:
          cuerpo = _noEncontrada(volver);
        case FaseDetalle.error:
          cuerpo = PaginaFlujo(
            titulo: TextosHistorial.titulo,
            tituloDiscreto: true,
            cabecera: const TituloFranja(TextosHistorial.tituloDetalle),
            children: [
              MensajeEstado(
                margen: 0,
                icono: Icons.error_outline_rounded,
                titulo: TextosHistorial.errorDetalle,
                etiquetaAccion: TextosHistorial.reintentar,
                alAccionar: notificador.cargar,
              ),
              OutlinedButton(
                onPressed: volver,
                child: const Text(TextosHistorial.volverAMisPagos),
              ),
            ],
          );
        case FaseDetalle.listo:
          // Igual que la web: "Mis pagos" junto a la flecha de regreso.
          cuerpo = ComprobanteVista(
            titulo: TextosHistorial.titulo,
            comprobante: estado.comprobante!,
            mostrarLogo: true,
            mostrarMotivo: true,
            aviso: estado.pendiente ? TextosHistorial.pendienteDetalle : null,
            acciones: [
              if (estado.pendiente)
                AccionResultado(
                  TextosHistorial.consultarDeNuevo,
                  estado.consultando ? null : notificador.consultarDeNuevo,
                  icono: Icons.schedule,
                ),
              AccionResultado(
                TextosHistorial.volverAMisPagos,
                volver,
                principal: !estado.pendiente,
                icono: Icons.receipt_long_outlined,
              ),
            ],
          );
      }
    }

    return AppScaffold(cuerpo: cuerpo);
  }

  Widget _noEncontrada(VoidCallback volver) {
    return PaginaFlujo(
      titulo: TextosHistorial.titulo,
      tituloDiscreto: true,
      cabecera: const TituloFranja(TextosHistorial.tituloDetalle),
      children: [
        MensajeEstado(
          margen: 0,
          vacio: true,
          icono: Icons.search,
          titulo: TextosHistorial.noEncontrada,
          etiquetaAccion: TextosHistorial.volverAMisPagos,
          alAccionar: volver,
        ),
      ],
    );
  }
}
