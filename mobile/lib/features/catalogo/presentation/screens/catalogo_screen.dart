import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../core/router/app_router.dart';
import '../../../../core/session/session_controller.dart';
import '../../../../shared/widgets/app_scaffold.dart';
import '../../../../shared/widgets/barra_secciones.dart';
import '../../../../shared/widgets/contenedor_ancho.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../widgets/banner_inicio.dart';
import '../../domain/entities/producto.dart';
import '../providers/catalogo_providers.dart';
import '../widgets/estados_catalogo.dart';
import '../widgets/filtros_catalogo.dart';
import '../widgets/tarjeta_producto.dart';

/// Primer nombre listo para el saludo ("ANA MARÍA" → "Ana").
String? primerNombre(String? nombreCompleto) {
  final partes = (nombreCompleto ?? '')
      .trim()
      .split(RegExp(r'\s+'))
      .where((p) => p.isNotEmpty);
  if (partes.isEmpty) return null;
  final p = partes.first;
  return p[0].toUpperCase() + p.substring(1).toLowerCase();
}

/// Inicio, igual que la web: en la franja negra el saludo, el buscador y los
/// accesos por categoría; debajo,
/// "Todos los servicios" con el conteo, la lista en una tarjeta y "Para ti".
class CatalogoScreen extends ConsumerWidget {
  const CatalogoScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final estado = ref.watch(catalogoProvider);
    final notificador = ref.read(catalogoProvider.notifier);
    final nombre = primerNombre(
      ref.watch(sessionControllerProvider.select((s) => s.nombreCliente)),
    );
    final colores = ColoresApp.de(context);
    final listo = estado.estado == EstadoCatalogo.listo;

    return AppScaffold(
      seccion: SeccionApp.inicio,
      cuerpo: MarcoBanda(
        marca: true,
        cabecera: Builder(
          // Colores de la franja (sobre negro), no los del contexto claro.
          builder: (context) {
            final colores = ColoresApp.de(context);
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Semantics(
                  header: true,
                  child: Text(
                    nombre == null ? 'Hola' : 'Hola, $nombre',
                    style: estiloTitular(
                      tamano: Escala.s28,
                      color: colores.texto,
                    ),
                  ),
                ),
                // Igual que la web: buscador y categorías solo con la lista lista.
                if (listo) ...[
                  // Igual que la web: el buscador va a 12 bajo el saludo, sin
                  // subtítulo.
                  const SizedBox(height: Esp.x3),
                  BuscadorCatalogo(
                    texto: estado.busqueda,
                    alCambiar: notificador.buscar,
                  ),
                  const SizedBox(height: Esp.x6),
                  FiltrosCategoria(
                    categorias: estado.categorias,
                    total: estado.productos.length,
                    seleccionada: estado.categoriaSeleccionada,
                    alSeleccionar: notificador.seleccionarCategoria,
                  ),
                ],
              ],
            );
          },
        ),
        cuerpo: RefreshIndicator(
          color: colores.marca,
          backgroundColor: colores.superficie,
          onRefresh: () => notificador.cargar(mostrarCarga: false),
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              const SliverToBoxAdapter(child: SizedBox(height: Esp.x6)),
              ..._slivers(context, estado, notificador),
              const SliverToBoxAdapter(child: SizedBox(height: Esp.x12)),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _slivers(
    BuildContext context,
    CatalogoState estado,
    CatalogoNotifier notificador,
  ) {
    switch (estado.estado) {
      case EstadoCatalogo.cargando:
        return [
          const SliverToBoxAdapter(
            child: AnuncioCarga(texto: 'Cargando servicios'),
          ),
          SliverTarjeta(
            cantidad: 6,
            sangria: 0,
            constructor: (context, i) => const EsqueletoTarjeta(),
          ),
        ];
      case EstadoCatalogo.vacio:
        return [
          SliverToBoxAdapter(
            child: MensajeEstado(
              vacio: true,
              accionSecundaria: true,
              icono: Icons.grid_view_outlined,
              titulo: 'Por ahora no hay servicios disponibles.',
              etiquetaAccion: 'Actualizar',
              alAccionar: () => notificador.cargar(),
            ),
          ),
        ];
      case EstadoCatalogo.error:
        return [
          SliverToBoxAdapter(
            child: MensajeEstado(
              icono: Icons.error_outline_rounded,
              titulo:
                  estado.mensajeError ?? 'Algo salió mal. Inténtalo de nuevo.',
              etiquetaAccion: 'Reintentar',
              alAccionar: () => notificador.cargar(),
            ),
          ),
        ];
      case EstadoCatalogo.listo:
        final filtrados = estado.filtrados;
        final cantidad = filtrados.length;
        return [
          _encabezado(
            TituloSeccion(
              estado.categoriaSeleccionada ?? 'Todos los servicios',
              detalle: cantidad == 1
                  ? '1 servicio encontrado'
                  : '$cantidad servicios encontrados',
            ),
          ),
          if (filtrados.isEmpty)
            SliverToBoxAdapter(
              child: MensajeEstado(
                vacio: true,
                icono: Icons.search,
                titulo: 'No encontramos servicios con esos filtros.',
                etiquetaAccion: 'Quitar filtros',
                alAccionar: notificador.quitarFiltros,
              ),
            )
          else
            SliverTarjeta(
              cantidad: filtrados.length,
              sangria: 0,
              constructor: (context, i) => _fila(context, filtrados[i]),
            ),
          // "Para ti": secundario, después de la lista.
          const SliverToBoxAdapter(child: SizedBox(height: Esp.x6)),
          _encabezado(const BannerInicio()),
        ];
    }
  }

  Widget _fila(BuildContext context, Producto p) {
    final idServicio = p.idServicio;
    final idProducto = p.idProducto;
    return FilaProducto(
      producto: p,
      alTocar: (idServicio == null || idProducto == null)
          ? null
          : () => context.push(Rutas.detalleCatalogo(idServicio, idProducto)),
    );
  }

  Widget _encabezado(Widget hijo) => SliverToBoxAdapter(
        child: ContenedorAncho(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: Esp.margen),
            child: SizedBox(width: double.infinity, child: hijo),
          ),
        ),
      );
}
