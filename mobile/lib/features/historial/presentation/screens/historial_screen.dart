import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../core/router/app_router.dart';
import '../../../../core/session/session_controller.dart';
import '../../../../shared/widgets/app_scaffold.dart';
import '../../../../shared/widgets/barra_secciones.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../../../catalogo/presentation/widgets/estados_catalogo.dart';
import '../../../catalogo/presentation/widgets/filtros_catalogo.dart';
import '../../domain/entities/elemento_historial.dart';
import '../../domain/utils/reglas_historial.dart';
import '../providers/historial_notifier.dart';
import '../providers/historial_providers.dart';
import '../textos_historial.dart';
import '../widgets/fila_pago.dart';

/// Encabezado del grupo de un movimiento: "Hoy", "Ayer" o la fecha.
String etiquetaDia(String? fechaTransaccion, DateTime ahora) {
  final dia = diaYHora(fechaTransaccion).dia;
  if (dia == null) return 'Sin fecha';
  var fecha = DateTime.tryParse((fechaTransaccion ?? '').trim());
  if (fecha != null) {
    if (fecha.isUtc) fecha = fecha.toLocal();
    final hoy = DateTime(ahora.year, ahora.month, ahora.day);
    final delDia = DateTime(fecha.year, fecha.month, fecha.day);
    final diferencia = hoy.difference(delDia).inDays;
    if (diferencia == 0) return 'Hoy';
    if (diferencia == 1) return 'Ayer';
  }
  return dia;
}

/// Orden de los filtros: Todos, Exitosos, Pendientes y Rechazados.
const List<FiltroHistorial> _ordenFiltros = [
  FiltroHistorial.todos,
  FiltroHistorial.exitosos,
  FiltroHistorial.pendientes,
  FiltroHistorial.rechazados,
];

/// Historial de pagos: filtros por estado sobre la franja negra y
/// movimientos agrupados por día en la hoja.
class HistorialScreen extends ConsumerWidget {
  const HistorialScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final estado = ref.watch(historialProvider);
    final notificador = ref.read(historialProvider.notifier);
    final colores = ColoresApp.de(context);
    final ahora = ref.read(relojProvider)();

    return AppScaffold(
      seccion: SeccionApp.pagos,
      cuerpo: MarcoBanda(
        marca: true,
        cabecera: Builder(
          // Colores de la franja (sobre negro), no los del contexto claro.
          builder: (context) {
            final colores = ColoresApp.de(context);
            final tema = Theme.of(context);
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Título y "Actualizar" en una fila, como la web.
                Row(
                  children: [
                    Expanded(
                      child: Semantics(
                        header: true,
                        child: Text(
                          TextosHistorial.titulo,
                          style: tema.textTheme.headlineMedium?.copyWith(
                            color: colores.texto,
                          ),
                        ),
                      ),
                    ),
                    OutlinedButton(
                      onPressed: estado.estado == EstadoHistorial.cargando
                          ? null
                          : () => notificador.cargar(),
                      child: const Text('Actualizar'),
                    ),
                  ],
                ),
                const SizedBox(height: Esp.x6),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  clipBehavior: Clip.none,
                  child: Row(
                    children: [
                      for (final f in _ordenFiltros) ...[
                        if (f != _ordenFiltros.first)
                          const SizedBox(width: Esp.x2),
                        ChipFiltro(
                          nombre: etiquetaFiltro(f),
                          activa: estado.filtro == f,
                          alTocar: () => notificador.cambiarFiltro(f),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            );
          },
        ),
        cuerpo: RefreshIndicator(
          color: colores.marca,
          backgroundColor: colores.superficie,
          onRefresh: notificador.actualizar,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              const SliverToBoxAdapter(child: SizedBox(height: Esp.x6)),
              ..._cuerpo(context, estado, notificador, ahora),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _cuerpo(
    BuildContext context,
    HistorialState estado,
    HistorialNotifier notificador,
    DateTime ahora,
  ) {
    switch (estado.estado) {
      case EstadoHistorial.cargando:
        return [
          const SliverToBoxAdapter(
            child: AnuncioCarga(texto: 'Cargando tus pagos'),
          ),
          SliverTarjeta(
            cantidad: 6,
            sangria: _sangria,
            constructor: (context, i) => const EsqueletoTarjeta(),
          ),
        ];
      case EstadoHistorial.vacio:
        final conFiltro = estado.filtro != FiltroHistorial.todos;
        return [
          SliverToBoxAdapter(
            child: MensajeEstado(
              vacio: true,
              icono: Icons.receipt_long_outlined,
              titulo: conFiltro
                  ? TextosHistorial.vacioFiltro
                  : TextosHistorial.vacioTitulo,
              etiquetaAccion: conFiltro ? null : TextosHistorial.irAlCatalogo,
              alAccionar: conFiltro ? null : () => context.go(Rutas.catalogo),
            ),
          ),
        ];
      case EstadoHistorial.error:
        return [
          SliverToBoxAdapter(
            child: MensajeEstado(
              icono: Icons.error_outline_rounded,
              titulo: TextosHistorial.errorLista,
              etiquetaAccion: TextosHistorial.reintentar,
              alAccionar: () => notificador.cargar(),
            ),
          ),
        ];
      case EstadoHistorial.listo:
        return [
          for (final (indice, grupo)
              in _agrupar(estado.elementos, ahora).indexed) ...[
            if (indice > 0)
              const SliverToBoxAdapter(child: SizedBox(height: Esp.x6)),
            _encabezado(TituloSeccion(grupo.dia, discreto: true)),
            SliverTarjeta(
              cantidad: grupo.elementos.length,
              sangria: _sangria,
              constructor: (context, i) {
                final e = grupo.elementos[i];
                return FilaPago(
                  elemento: e,
                  alTocar: e.upc == null
                      ? null
                      : () => context.push(Rutas.detalleHistorial(e.upc!)),
                );
              },
            ),
          ],
          if (estado.hayMas || estado.errorMas)
            SliverToBoxAdapter(
              child: _VerMas(
                cargando: estado.cargandoMas,
                conError: estado.errorMas,
                alPulsar: notificador.verMas,
              ),
            ),
          const SliverToBoxAdapter(child: SizedBox(height: Esp.x8)),
        ];
    }
  }

  static const double _sangria = 0;

  /// Agrupa los movimientos consecutivos del mismo día (conserva el orden
  /// de la lista).
  List<({String dia, List<ElementoHistorial> elementos})> _agrupar(
    List<ElementoHistorial> elementos,
    DateTime ahora,
  ) {
    final grupos = <({String dia, List<ElementoHistorial> elementos})>[];
    for (final e in elementos) {
      final dia = etiquetaDia(e.fechaTransaccion, ahora);
      if (grupos.isEmpty || grupos.last.dia != dia) {
        grupos.add((dia: dia, elementos: <ElementoHistorial>[]));
      }
      grupos.last.elementos.add(e);
    }
    return grupos;
  }

  Widget _encabezado(Widget hijo) => SliverToBoxAdapter(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: Medidas.anchoContenido,
            ),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                Esp.x4,
                Esp.x6,
                Esp.x4,
                0,
              ),
              child: SizedBox(width: double.infinity, child: hijo),
            ),
          ),
        ),
      );
}

class _VerMas extends StatelessWidget {
  const _VerMas({
    required this.cargando,
    required this.conError,
    required this.alPulsar,
  });

  final bool cargando;
  final bool conError;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: Medidas.anchoContenido),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(Esp.x4, Esp.x6, Esp.x4, 0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (conError) ...[
                Semantics(
                  liveRegion: true,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.error_outline, color: colores.error, size: 20),
                      const SizedBox(width: Esp.x2),
                      Expanded(
                        child: Text(
                          TextosHistorial.errorMas,
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: Esp.x3),
              ],
              OutlinedButton(
                onPressed: cargando ? null : alPulsar,
                child: Text(cargando ? 'Cargando…' : TextosHistorial.verMas),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
