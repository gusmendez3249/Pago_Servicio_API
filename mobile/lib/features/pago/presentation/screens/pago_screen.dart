import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../core/router/app_router.dart';
import '../../../../shared/widgets/app_scaffold.dart';
import '../../../../shared/widgets/estado_operacion.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../../../../shared/widgets/pagina_flujo.dart';
import '../../../catalogo/domain/utils/formato_catalogo.dart';
import '../../../catalogo/domain/entities/producto.dart';
import '../../../catalogo/presentation/providers/catalogo_providers.dart';
import '../../../catalogo/presentation/widgets/cabecera_servicio.dart';
import '../../../catalogo/presentation/widgets/estados_catalogo.dart';
import '../../../catalogo/presentation/widgets/tarjeta_producto.dart';
import '../../../referencia/presentation/providers/referencia_providers.dart';
import '../../domain/entities/comprobante_pago.dart';
import '../../domain/entities/producto_pago.dart';
import '../providers/pago_notifier.dart';
import '../providers/pago_providers.dart';
import '../providers/pago_state.dart';
import '../textos_pago.dart';
import '../widgets/captura_datos.dart';
import '../widgets/dialogo_pin.dart';
import '../widgets/resultado_vista.dart';
import '../widgets/resumen_pago.dart';
import 'comprobante_screen.dart';

/// Flujo de pago: captura, resumen, confirmación con PIN y resultado.
class PagoScreen extends ConsumerStatefulWidget {
  const PagoScreen({
    super.key,
    required this.idServicio,
    required this.idProducto,
  });

  final int? idServicio;
  final int? idProducto;

  @override
  ConsumerState<PagoScreen> createState() => _PagoScreenState();
}

class _PagoScreenState extends ConsumerState<PagoScreen> {
  late final ReferenciaNotifier _referencia;
  bool _arrancado = false;
  bool _dialogoAbierto = false;
  FasePago _ultimaFase = FasePago.preparando;

  @override
  void initState() {
    super.initState();
    _referencia = ref.read(referenciaProvider.notifier);
  }

  @override
  void dispose() {
    // Salir antes de terminar invalida la validación de la referencia (el
    // comprobante ya no sirve); se conserva solo el texto escrito.
    final terminado = _ultimaFase == FasePago.exitoso ||
        _ultimaFase == FasePago.rechazado ||
        _ultimaFase == FasePago.error;
    if (!terminado) {
      final referencia = _referencia;
      Future<void>.microtask(referencia.invalidarValidacion);
    }
    super.dispose();
  }

  ClavePago? get _clave {
    final ids = widget.idServicio;
    final idp = widget.idProducto;
    return (ids == null || idp == null) ? null : ClavePago(ids, idp);
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

  void _arrancar(ClavePago clave, Producto producto) {
    if (_arrancado) return;
    _arrancado = true;
    final datos = ref.read(referenciaProvider).datosPago;
    final notifier = ref.read(pagoProvider(clave).notifier);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      notifier.iniciar(
        ProductoPago(
          idServicio: clave.idServicio,
          idProducto: clave.idProducto,
          tipoFront: producto.tipoFront,
          servicio: nombreServicio(producto),
          producto: producto.producto?.trim() ?? '',
          precio: producto.precio,
          logoUrl: producto.logoUrl,
        ),
        datosReferencia: datos,
      );
    });
  }

  void _alCambiarEstado(
      PagoState? anterior, PagoState actual, ClavePago clave) {
    _ultimaFase = actual.fase;
    if (actual.fase == FasePago.confirmando &&
        anterior?.fase != FasePago.confirmando) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _abrirDialogoPin(clave);
      });
    }
  }

  Future<void> _abrirDialogoPin(ClavePago clave) async {
    final estado = ref.read(pagoProvider(clave));
    if (_dialogoAbierto || estado.fase != FasePago.confirmando) return;
    _dialogoAbierto = true;
    final pin = await mostrarDialogoPin(
      context,
      total: estado.total ?? 0,
      aviso: estado.avisoPin
          ? TextosPago.pinIncorrecto(estado.intentosRestantes)
          : null,
    );
    _dialogoAbierto = false;
    if (!mounted) return;
    final notifier = ref.read(pagoProvider(clave).notifier);
    if (pin == null) {
      notifier.cancelarConfirmacion();
    } else {
      await notifier.confirmar(pin);
    }
  }

  @override
  Widget build(BuildContext context) {
    final clave = _clave;
    if (clave == null) {
      return AppScaffold(cuerpo: _mensaje(context, Icons.search_off));
    }

    final estado = ref.watch(pagoProvider(clave));
    ref.listen<PagoState>(
      pagoProvider(clave),
      (anterior, actual) => _alCambiarEstado(anterior, actual, clave),
    );
    final catalogo = ref.watch(catalogoProvider);

    Widget cuerpo;
    if (catalogo.estado == EstadoCatalogo.cargando) {
      cuerpo = const _Cargando(texto: TextosPago.preparando);
    } else if (catalogo.estado == EstadoCatalogo.error) {
      cuerpo = _mensaje(context, Icons.cloud_off_outlined);
    } else {
      final producto = _buscar(catalogo.productos);
      if (producto == null) {
        cuerpo = _mensaje(context, Icons.search_off);
      } else {
        _arrancar(clave, producto);
        cuerpo = _cuerpoPorFase(context, clave, estado);
      }
    }

    final bloqueado = estado.fase == FasePago.procesando || estado.enVuelo;
    return PopScope(
      canPop: !bloqueado,
      child: AppScaffold(cuerpo: cuerpo),
    );
  }

  Widget _mensaje(BuildContext context, IconData icono) {
    return PaginaFlujo(
      paso: 3,
      cabecera: const TituloFranja(TextosPago.genericoTitulo),
      children: [
        MensajeEstado(
          margen: 0,
          vacio: true,
          icono: icono,
          titulo: TextosPago.genericoTitulo,
          detalle: TextosPago.genericoMensaje,
          etiquetaAccion: TextosPago.volver,
          alAccionar: () => context.go(Rutas.catalogo),
        ),
      ],
    );
  }

  /// Franja del flujo, igual que la web: logo, título de la fase, "servicio,
  /// producto" y, si ya se conoce, el importe a pagar.
  Widget? _cabeceraServicio(PagoState e, String titulo) {
    final p = e.producto;
    if (p == null) return TituloFranja(titulo);
    final total = e.total;
    return CabeceraServicio(
      logoUrl: p.logoUrl,
      nombreLogo: p.servicio,
      titulo: titulo,
      subtitulo: [p.servicio, if (p.producto.isNotEmpty) p.producto].join(', '),
      etiquetaDato: total == null ? null : TextosPago.importeAPagar,
      dato: total == null ? null : formatearMoneda(total),
    );
  }

  /// Tarjeta "Tu pago" de la captura.
  Widget? _tuPago(PagoState e) {
    final p = e.producto;
    if (p == null) return null;
    return TarjetaDatos(
      relleno: const EdgeInsets.fromLTRB(Esp.x6, Esp.x6, Esp.x6, Esp.x3),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            TextosPago.tuPago,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: Esp.x4),
          FichaDatos(
            filas: [
              FilaDato(TextosPago.servicio, p.servicio),
              FilaDato(
                TextosPago.producto,
                p.producto.isEmpty ? '—' : p.producto,
              ),
              if (e.esReferencia) FilaDato(TextosPago.referencia, e.referencia),
            ],
          ),
        ],
      ),
    );
  }

  Widget _cuerpoPorFase(BuildContext context, ClavePago clave, PagoState e) {
    final notifier = ref.read(pagoProvider(clave).notifier);
    switch (e.fase) {
      case FasePago.preparando:
        return const _Cargando(texto: TextosPago.preparando);
      case FasePago.capturandoDatos:
        return e.captura == CapturaPago.importe
            ? CapturaImporte(
                error: e.errorImporte,
                alContinuar: notifier.capturarImporte,
                cabecera: _cabeceraServicio(e, TextosPago.datosPago),
                tuPago: _tuPago(e),
              )
            : CapturaTelefono(
                error: e.errorTelefono,
                alContinuar: notifier.capturarTelefono,
                cabecera: _cabeceraServicio(e, TextosPago.capturaTelefono),
                tuPago: _tuPago(e),
              );
      case FasePago.resumen:
      case FasePago.confirmando:
        return _resumen(context, clave, e, notifier);
      case FasePago.procesando:
        // Con el pago en proceso no se puede cancelar (igual que la web).
        return const _Cargando(
          texto: TextosPago.procesando,
          cancelable: false,
        );
      case FasePago.exitoso:
        return _exitoso(context, e);
      case FasePago.rechazado:
        return _rechazado(context, clave, e, notifier);
      case FasePago.pendiente:
        return _pendiente(context, e, notifier);
      case FasePago.error:
        return _error(context, clave, e);
    }
  }

  Widget _resumen(
    BuildContext context,
    ClavePago clave,
    PagoState e,
    PagoNotifier notifier,
  ) {
    return ResumenPagoVista(
      estado: e,
      alPagar: notifier.pedirConfirmacion,
      alCambiarDatos: () => context.go(Rutas.catalogo),
      alVolver: () =>
          context.go(Rutas.detalleCatalogo(clave.idServicio, clave.idProducto)),
      alSeleccionarCuenta: notifier.seleccionarCuenta,
    );
  }

  Widget _exitoso(BuildContext context, PagoState e) {
    final c = e.comprobante;
    final servicio = c?.servicio ?? e.producto?.servicio ?? '';
    final total = e.totalMostrado ?? 0;
    final folio =
        (c?.tieneFolio ?? false) ? c!.folio : c?.identificadorOperacion;
    return ResultadoVista(
      tipo: TipoEstado.exito,
      titulo: TextosPago.exitosoTitulo,
      etiquetaImporte: TextosPago.pagaste,
      importe: formatearMoneda(total),
      mensajes: [
        TextosPago.exitosoMensaje(
            servicio: servicio, total: total, folio: folio),
      ],
      // Mismo orden que la web.
      acciones: [
        AccionResultado(
          TextosPago.verComprobante,
          c == null ? null : () => _verComprobante(context, c, e),
        ),
        AccionResultado(
          TextosPago.verMisPagos,
          () => context.go(Rutas.historial),
          principal: false,
        ),
        AccionResultado(
          TextosPago.irInicio,
          () => context.go(Rutas.inicio),
          principal: false,
        ),
      ],
    );
  }

  void _verComprobante(BuildContext context, ComprobantePago c, PagoState e) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ComprobanteScreen(
          comprobante: c,
          servicio: e.producto?.servicio,
          producto: e.producto?.producto,
          total: e.totalMostrado,
        ),
      ),
    );
  }

  Widget _rechazado(
    BuildContext context,
    ClavePago clave,
    PagoState e,
    PagoNotifier notifier,
  ) {
    final motivo = e.motivoRechazo ?? MotivoPago.rechazadoProveedor;
    final String texto;
    final AccionResultado accion;
    void irAReferencia() =>
        context.go(Rutas.referencia(clave.idServicio, clave.idProducto));
    switch (motivo) {
      case MotivoPago.referenciaInvalida:
        texto = TextosPago.referenciaInvalida;
        accion =
            AccionResultado(TextosPago.validarOtraReferencia, irAReferencia);
      case MotivoPago.errorGestoPago:
        texto = TextosPago.errorGestoPago;
        accion = AccionResultado(
          TextosPago.entendido,
          () => context.go(Rutas.inicio),
        );
      case MotivoPago.servicioNoDisponible:
      case MotivoPago.proveedorNoDisponible:
        texto = TextosPago.servicioNoDisponible;
        accion = AccionResultado(
          TextosPago.entendido,
          () => context.go(Rutas.inicio),
        );
      default:
        texto = TextosPago.rechazadoProveedor;
        accion = AccionResultado(
          TextosPago.intentarDeNuevo,
          e.esReferencia ? irAReferencia : notifier.reiniciar,
        );
    }
    return ResultadoVista(
      tipo: TipoEstado.error,
      titulo: motivo == MotivoPago.errorGestoPago
          ? TextosPago.errorGestoPagoTitulo
          : TextosPago.rechazadoTitulo,
      mensajes: [texto, TextosPago.sinCargo],
      acciones: [
        accion,
        AccionResultado(
          TextosPago.verMisPagos,
          () => context.go(Rutas.historial),
          principal: false,
        ),
      ],
    );
  }

  Widget _pendiente(BuildContext context, PagoState e, PagoNotifier notifier) {
    final String titulo;
    final List<String> mensajes;
    if (e.pagoNoLlego) {
      titulo = TextosPago.noLlegoTitulo;
      mensajes = [TextosPago.noLlegoMensaje];
    } else if (e.sinConexion && e.upc == null) {
      titulo = TextosPago.sinConexionTitulo;
      mensajes = [TextosPago.sinConexionMensaje];
    } else {
      titulo = TextosPago.pendienteTitulo;
      mensajes = [TextosPago.pendienteMensaje(e.upc)];
    }
    return ResultadoVista(
      tipo: TipoEstado.pendiente,
      titulo: titulo,
      etiquetaImporte: TextosPago.importeResumen,
      importe:
          e.totalMostrado == null ? null : formatearMoneda(e.totalMostrado!),
      mensajes: mensajes,
      cargando: e.sondeando || e.consultando,
      // Mismo orden que la web.
      acciones: [
        if (e.pagoNoLlego)
          AccionResultado(TextosPago.reintentarPago, notifier.reintentarPago),
        AccionResultado(
          TextosPago.consultarDeNuevo,
          e.consultando ? null : notifier.consultarDeNuevo,
          principal: !e.pagoNoLlego,
        ),
        AccionResultado(
          TextosPago.verMisPagos,
          () => context.go(Rutas.historial),
          principal: false,
        ),
        AccionResultado(
          TextosPago.irInicio,
          () => context.go(Rutas.inicio),
          principal: false,
        ),
      ],
    );
  }

  Widget _error(BuildContext context, ClavePago clave, PagoState e) {
    switch (e.error ?? ErrorPago.generico) {
      case ErrorPago.pinBloqueado:
        return ResultadoVista(
          tipo: TipoEstado.error,
          titulo: TextosPago.pinBloqueadoTitulo,
          mensajes: const [TextosPago.pinBloqueadoMensaje],
          acciones: [
            AccionResultado(
                TextosPago.irInicio, () => context.go(Rutas.inicio)),
          ],
        );
      case ErrorPago.validacionExpirada:
        return ResultadoVista(
          tipo: TipoEstado.error,
          titulo: TextosPago.validacionExpiradaTitulo,
          mensajes: const [TextosPago.validacionExpiradaMensaje],
          acciones: [
            AccionResultado(
              TextosPago.validarDeNuevo,
              () => context.go(
                Rutas.referencia(clave.idServicio, clave.idProducto),
              ),
            ),
          ],
        );
      case ErrorPago.sinCuenta:
        return ResultadoVista(
          tipo: TipoEstado.error,
          titulo: TextosPago.sinCuentaTitulo,
          mensajes: const [TextosPago.sinCuentaMensaje],
          acciones: [
            AccionResultado(
                TextosPago.irInicio, () => context.go(Rutas.inicio)),
          ],
        );
      case ErrorPago.generico:
        return ResultadoVista(
          tipo: TipoEstado.error,
          titulo: TextosPago.genericoTitulo,
          mensajes: const [TextosPago.genericoMensaje],
          acciones: [
            AccionResultado(
              TextosPago.volver,
              () => context.go(
                Rutas.detalleCatalogo(clave.idServicio, clave.idProducto),
              ),
            ),
          ],
        );
    }
  }
}

class _Cargando extends StatelessWidget {
  const _Cargando({required this.texto, this.cancelable = true});

  final String texto;
  final bool cancelable;

  @override
  Widget build(BuildContext context) {
    return PaginaFlujo(
      paso: 3,
      cancelable: cancelable,
      cabecera: const TituloFranja(TextosPago.preparandoTitulo),
      children: [LineaCargando(texto: texto)],
    );
  }
}
