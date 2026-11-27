import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../core/router/app_router.dart';
import '../../../../shared/widgets/app_scaffold.dart';
import '../../../../shared/widgets/pagina_flujo.dart';
import '../../../catalogo/domain/entities/producto.dart';
import '../../../catalogo/presentation/providers/catalogo_providers.dart';
import '../../../catalogo/presentation/widgets/cabecera_servicio.dart';
import '../../../catalogo/presentation/widgets/estados_catalogo.dart';
import '../../../catalogo/presentation/widgets/logo_servicio.dart';
import '../../../../shared/widgets/enlace.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../../domain/utils/formato_referencia.dart';
import '../../domain/utils/guard_pago.dart';
import '../providers/referencia_providers.dart';
import '../textos_referencia.dart';
import '../widgets/tarjeta_resultado_referencia.dart';

/// Captura y resultado de la validación de referencia (una sola pantalla que
/// cambia según el estado).
class ReferenciaScreen extends ConsumerStatefulWidget {
  const ReferenciaScreen({
    super.key,
    required this.idServicio,
    required this.idProducto,
  });

  final int? idServicio;
  final int? idProducto;

  @override
  ConsumerState<ReferenciaScreen> createState() => _ReferenciaScreenState();
}

class _ReferenciaScreenState extends ConsumerState<ReferenciaScreen> {
  final TextEditingController _controlador = TextEditingController();
  late final ReferenciaNotifier _notifier;

  @override
  void initState() {
    super.initState();
    _notifier = ref.read(referenciaProvider.notifier);
    final ids = widget.idServicio;
    final idp = widget.idProducto;
    if (ids != null && idp != null) {
      final actual = ref.read(referenciaProvider);
      if (actual.perteneceA(ids, idp)) _controlador.text = actual.referencia;
      // No se puede modificar un provider mientras se construye el árbol.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _notifier.iniciar(ids, idp);
      });
    }
  }

  @override
  void dispose() {
    _controlador.dispose();
    final ids = widget.idServicio;
    final idp = widget.idProducto;
    if (ids != null && idp != null) {
      // Salir del flujo invalida la validación. Se difiere para no tocar el
      // provider durante el desmontaje del árbol.
      final notifier = _notifier;
      Future<void>.microtask(() => notifier.salir(ids, idp));
    }
    super.dispose();
  }

  Producto? _buscar(List<Producto> productos) {
    for (final p in productos) {
      if (p.idServicio == widget.idServicio &&
          p.idProducto == widget.idProducto) {
        return p;
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final catalogo = ref.watch(catalogoProvider);

    Widget cuerpo;
    switch (catalogo.estado) {
      case EstadoCatalogo.cargando:
        cuerpo = const PaginaFlujo(
          paso: 2,
          cabecera: TituloFranja(_titulo),
          children: [
            LineaCargando(texto: 'Cargando información del servicio…')
          ],
        );
      case EstadoCatalogo.error:
        cuerpo = PaginaFlujo(
          paso: 2,
          cabecera: const TituloFranja(_titulo),
          children: [
            MensajeEstado(
              margen: 0,
              icono: Icons.error_outline_rounded,
              titulo: catalogo.mensajeError ??
                  'Algo salió mal. Inténtalo de nuevo.',
              etiquetaAccion: 'Reintentar',
              alAccionar: () => ref.read(catalogoProvider.notifier).cargar(),
            ),
          ],
        );
      case EstadoCatalogo.vacio:
      case EstadoCatalogo.listo:
        final producto = _buscar(catalogo.productos);
        if (producto == null) {
          cuerpo = PaginaFlujo(
            paso: 2,
            cabecera: const TituloFranja(_titulo),
            children: [
              MensajeEstado(
                margen: 0,
                vacio: true,
                icono: Icons.search,
                titulo: 'No encontramos este servicio. Puede que ya no esté '
                    'disponible.',
                etiquetaAccion: 'Ir al inicio',
                alAccionar: () => context.go(Rutas.catalogo),
              ),
            ],
          );
        } else if (!tipoFrontAdmiteReferencia(producto.tipoFront)) {
          cuerpo = PaginaFlujo(
            paso: 2,
            cabecera: const TituloFranja(_titulo),
            children: [
              MensajeEstado(
                margen: 0,
                vacio: true,
                icono: Icons.schedule_rounded,
                titulo: TextosReferencia.pagoNoDisponibleProducto,
                etiquetaAccion: 'Ir al inicio',
                alAccionar: () => context.go(Rutas.catalogo),
              ),
            ],
          );
        } else {
          cuerpo = _Formulario(
            producto: producto,
            idServicio: widget.idServicio!,
            idProducto: widget.idProducto!,
            controlador: _controlador,
          );
        }
    }

    return AppScaffold(cuerpo: cuerpo);
  }
}

const String _titulo = 'Captura tu referencia';

/// Captura y validación, igual que la web: tarjeta con el campo, la ayuda y
/// "Validar referencia"; el resultado; la tarjeta "Tu pago" y "Continuar al
/// pago" (habilitado solo con la referencia vigente).
class _Formulario extends ConsumerStatefulWidget {
  const _Formulario({
    required this.producto,
    required this.idServicio,
    required this.idProducto,
    required this.controlador,
  });

  final Producto producto;
  final int idServicio;
  final int idProducto;
  final TextEditingController controlador;

  @override
  ConsumerState<_Formulario> createState() => _FormularioState();
}

class _FormularioState extends ConsumerState<_Formulario> {
  bool _ayudaVisible = false;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final colores = ColoresApp.de(context);
    final producto = widget.producto;
    final notifier = ref.read(referenciaProvider.notifier);
    final global = ref.watch(referenciaProvider);
    // Mientras se hace el cambio de producto se muestra un estado limpio.
    final vm = global.perteneceA(widget.idServicio, widget.idProducto)
        ? global
        : const ReferenciaState();

    final validando = vm.estado == EstadoReferencia.validando;
    final ayuda = producto.ayudaUrl;
    final puedeContinuar = vm.puedeContinuar;
    final hayResultado = textoResultadoReferencia(vm) != null;
    final servicio = producto.servicio?.trim() ?? '';
    final nombreProducto = producto.producto?.trim() ?? '';

    void validar() {
      FocusScope.of(context).unfocus();
      notifier.validar();
    }

    void continuar() {
      notifier.verificarVigencia();
      if (ref.read(referenciaProvider).puedeContinuar) {
        context.push(Rutas.pago(widget.idServicio, widget.idProducto));
      }
    }

    String? errorTexto;
    if (vm.estado == EstadoReferencia.formatoInvalido &&
        vm.errorFormato != null) {
      errorTexto = TextosReferencia.errorFormato(vm.errorFormato!);
    }

    final contenidoValidar = validando
        ? Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                width: Medidas.giro,
                height: Medidas.giro,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: colores.deshabilitado,
                ),
              ),
              const SizedBox(width: Esp.x2),
              const Flexible(child: Text('Validando…')),
            ],
          )
        : const Text('Validar referencia');
    // Con la referencia vigente, "Validar" pasa a secundario.
    final botonValidar = puedeContinuar
        ? OutlinedButton(
            onPressed: validando ? null : validar,
            child: contenidoValidar,
          )
        : FilledButton(
            onPressed: validando ? null : validar,
            child: contenidoValidar,
          );

    return PaginaFlujo(
      paso: 2,
      cabecera: CabeceraServicio(
        logoUrl: producto.logoUrl,
        nombreLogo: servicio,
        titulo: _titulo,
        subtitulo: [
          if (servicio.isNotEmpty) servicio else 'Servicio',
          if (nombreProducto.isNotEmpty) nombreProducto,
        ].join(', '),
      ),
      pie: FilledButton(
        onPressed: puedeContinuar ? continuar : null,
        child: const Text('Continuar al pago'),
      ),
      children: [
        TarjetaDatos(
          relleno: const EdgeInsets.all(Esp.x6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                controller: widget.controlador,
                readOnly: validando,
                maxLength: kLongitudMaximaReferencia,
                autocorrect: false,
                enableSuggestions: false,
                textInputAction: TextInputAction.done,
                style: estiloCampoCifras(),
                decoration: InputDecoration(
                  labelText: 'Referencia de pago',
                  helperText: errorTexto == null
                      ? 'Solo letras y números, sin espacios.'
                      : null,
                  errorText: errorTexto,
                  errorMaxLines: 3,
                  counterText: '',
                ),
                onChanged: notifier.editar,
                onSubmitted: (_) {
                  if (!validando) validar();
                },
              ),
              if (urlDeImagenUsable(ayuda)) ...[
                const SizedBox(height: Esp.x2),
                Enlace(
                  texto: TextosReferencia.ayuda,
                  alTocar: () => setState(() => _ayudaVisible = !_ayudaVisible),
                ),
                if (_ayudaVisible) ...[
                  const SizedBox(height: Esp.x2),
                  ImagenAyuda(url: ayuda!),
                ],
              ],
              const SizedBox(height: Esp.x6),
              botonValidar,
              if (validando) ...[
                const SizedBox(height: Esp.x4),
                const LineaCargando(
                  texto: 'Estamos verificando tu referencia…',
                ),
              ],
              if (hayResultado) ...[
                const SizedBox(height: Esp.x4),
                TarjetaResultadoReferencia(vm: vm),
                if (vm.estado == EstadoReferencia.error ||
                    vm.estado == EstadoReferencia.expirada) ...[
                  const SizedBox(height: Esp.x3),
                  OutlinedButton(
                    onPressed: validar,
                    child: Text(
                      vm.estado == EstadoReferencia.error
                          ? 'Reintentar'
                          : 'Validar de nuevo',
                    ),
                  ),
                ],
              ],
            ],
          ),
        ),
        const SizedBox(height: Esp.x4),
        TarjetaDatos(
          relleno: const EdgeInsets.fromLTRB(Esp.x6, Esp.x6, Esp.x6, Esp.x3),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Tu pago', style: tema.textTheme.titleMedium),
              const SizedBox(height: Esp.x4),
              FichaDatos(
                filas: [
                  FilaDato('Servicio', servicio.isEmpty ? '—' : servicio),
                  FilaDato(
                    'Producto',
                    nombreProducto.isEmpty ? '—' : nombreProducto,
                  ),
                  FilaDato(
                    'Referencia',
                    hayResultado ? vm.referencia.trim() : '—',
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}
