import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../core/router/app_router.dart';
import '../../../../shared/widgets/app_scaffold.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../../../../shared/widgets/pagina_flujo.dart';
import '../../../referencia/domain/utils/guard_pago.dart';
import '../../../referencia/presentation/textos_referencia.dart';
import '../../domain/entities/producto.dart';
import '../../domain/utils/formato_catalogo.dart';
import '../providers/catalogo_providers.dart';
import '../widgets/cabecera_servicio.dart';
import '../widgets/estados_catalogo.dart';
import '../widgets/logo_servicio.dart';

const String _titulo = 'Detalle del servicio';

/// Detalle de un producto del catálogo (se busca en la lista ya cargada),
/// igual que la web: franja con logo, nombre, producto e importe; tarjeta con
/// la descripción y los datos del servicio; "Continuar" anclado abajo.
class DetalleProductoScreen extends ConsumerWidget {
  const DetalleProductoScreen({
    super.key,
    required this.idServicio,
    required this.idProducto,
  });

  final int? idServicio;
  final int? idProducto;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final estado = ref.watch(catalogoProvider);

    Widget cuerpo;
    switch (estado.estado) {
      case EstadoCatalogo.cargando:
        cuerpo = const PaginaFlujo(
          paso: 1,
          cabecera: TituloFranja(_titulo),
          children: [
            LineaCargando(texto: 'Cargando información del servicio…')
          ],
        );
      case EstadoCatalogo.error:
        cuerpo = PaginaFlujo(
          paso: 1,
          cabecera: const TituloFranja(_titulo),
          children: [
            MensajeEstado(
              margen: 0,
              icono: Icons.error_outline_rounded,
              titulo:
                  estado.mensajeError ?? 'Algo salió mal. Inténtalo de nuevo.',
              etiquetaAccion: 'Reintentar',
              alAccionar: () => ref.read(catalogoProvider.notifier).cargar(),
            ),
          ],
        );
      case EstadoCatalogo.vacio:
      case EstadoCatalogo.listo:
        final producto = _buscar(estado.productos);
        cuerpo = producto == null
            ? const _NoEncontrado()
            : _Detalle(producto: producto);
    }

    return AppScaffold(cuerpo: cuerpo);
  }

  Producto? _buscar(List<Producto> productos) {
    for (final p in productos) {
      if (p.idServicio == idServicio && p.idProducto == idProducto) return p;
    }
    return null;
  }
}

class _NoEncontrado extends StatelessWidget {
  const _NoEncontrado();

  @override
  Widget build(BuildContext context) {
    return PaginaFlujo(
      paso: 1,
      cabecera: const TituloFranja(_titulo),
      children: [
        MensajeEstado(
          margen: 0,
          vacio: true,
          icono: Icons.search,
          titulo:
              'No encontramos este servicio. Puede que ya no esté disponible.',
          etiquetaAccion: 'Ir al inicio',
          alAccionar: () => context.go(Rutas.catalogo),
        ),
      ],
    );
  }
}

class _Detalle extends StatelessWidget {
  const _Detalle({required this.producto});

  final Producto producto;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final legend = producto.legend?.trim() ?? '';
    final ayuda = producto.ayudaUrl;
    final esTelefono = producto.tipoFront == kTipoFrontPagoTelefono &&
        (producto.precio ?? 0) > 0;
    final admiteReferencia = tipoFrontAdmiteReferencia(producto.tipoFront);
    final puedeContinuar = (admiteReferencia || esTelefono) &&
        producto.idServicio != null &&
        producto.idProducto != null;
    final precio = producto.precio;
    final servicio = producto.servicio?.trim() ?? '';

    final String nota;
    if (admiteReferencia) {
      nota = 'Ten a la mano tu recibo: en el siguiente paso validamos tu '
          'referencia.';
    } else if (esTelefono) {
      nota = 'En el siguiente paso capturas el número de teléfono.';
    } else {
      nota = TextosReferencia.pagoNoDisponibleProducto;
    }

    return PaginaFlujo(
      paso: 1,
      segundoPaso: esTelefono ? 'Teléfono' : 'Referencia',
      cabecera: CabeceraServicio(
        logoUrl: producto.logoUrl,
        nombreLogo: servicio,
        titulo: servicio.isEmpty ? 'Servicio' : servicio,
        subtitulo: producto.producto,
        etiquetaDato: precio == null ? null : producto.etiquetaImporte,
        dato: precio == null ? null : formatearMoneda(precio),
      ),
      pie: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FilledButton(
            onPressed: puedeContinuar
                ? () => context.push(
                      esTelefono
                          ? Rutas.pago(
                              producto.idServicio!,
                              producto.idProducto!,
                            )
                          : Rutas.referencia(
                              producto.idServicio!,
                              producto.idProducto!,
                            ),
                    )
                : null,
            child: const Text('Continuar'),
          ),
        ],
      ),
      children: [
        // Igual que la web: el siguiente paso, como primera tarjeta.
        TarjetaDatos(
          relleno: const EdgeInsets.all(Esp.x4),
          child: Text(nota, style: tema.textTheme.bodyMedium),
        ),
        const SizedBox(height: Esp.x4),
        TarjetaDatos(
          relleno: const EdgeInsets.all(Esp.x6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (legend.isNotEmpty) ...[
                Text('Descripción', style: tema.textTheme.titleMedium),
                const SizedBox(height: Esp.x3),
                Text(legend, style: tema.textTheme.bodyLarge),
                const SizedBox(height: Esp.x6),
                const Divider(),
                const SizedBox(height: Esp.x6),
              ],
              Text('Datos del servicio', style: tema.textTheme.titleMedium),
              const SizedBox(height: Esp.x3),
              FichaDatos(
                // Solo datos que le dicen algo al cliente: sin los
                // identificadores internos ni el código de tipo de referencia
                // del proveedor.
                filas: [
                  FilaDato('Categoría', producto.categoriaNombre),
                  FilaDato(
                    'Dígito verificador',
                    (producto.hasDigitoVerificador ?? false) ? 'Sí' : 'No',
                  ),
                ],
              ),
              if (urlDeImagenUsable(ayuda)) ...[
                const SizedBox(height: Esp.x6),
                const Divider(),
                const SizedBox(height: Esp.x6),
                Text(
                  'Dónde encontrar tu referencia',
                  style: tema.textTheme.titleMedium,
                ),
                const SizedBox(height: Esp.x3),
                ImagenAyuda(url: ayuda!),
              ],
            ],
          ),
        ),
      ],
    );
  }
}
