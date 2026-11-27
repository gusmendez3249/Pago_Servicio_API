import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/http/idempotency.dart';
import '../../../../core/http/result.dart';
import '../../../referencia/domain/entities/validacion_referencia.dart';
import '../../domain/entities/comprobante_pago.dart';
import '../../domain/entities/cuenta_cliente.dart';
import '../../domain/entities/producto_pago.dart';
import '../../domain/entities/salida_pago.dart';
import '../../domain/entities/solicitud_pago.dart';
import '../../domain/usecases/pago_usecases.dart';
import '../../domain/utils/reglas_pago.dart';
import 'pago_state.dart';

/// Identifica el flujo de pago de un producto.
class ClavePago {
  const ClavePago(this.idServicio, this.idProducto);

  final int idServicio;
  final int idProducto;

  @override
  bool operator ==(Object other) =>
      other is ClavePago &&
      other.idServicio == idServicio &&
      other.idProducto == idProducto;

  @override
  int get hashCode => Object.hash(idServicio, idProducto);
}

/// Máquina de estados del pago: preparando → capturandoDatos → resumen →
/// confirmando → procesando → exitoso | rechazado | pendiente | error.
class PagoNotifier extends StateNotifier<PagoState> {
  PagoNotifier({
    required ObtenerCuentasUseCase obtenerCuentas,
    required PagarServicioUseCase pagar,
    required ConsultarEstadoPagoUseCase consultarEstado,
    List<Duration> intervalosSondeo = kIntervalosSondeo,
    String Function()? generarLlave,
    void Function()? alTerminar,
  })  : _obtenerCuentas = obtenerCuentas,
        _pagar = pagar,
        _consultarEstado = consultarEstado,
        _esperas = esperasEntreConsultas(intervalosSondeo),
        _generarLlave = generarLlave ?? nuevaIdempotencyKey,
        _alTerminar = alTerminar,
        super(const PagoState());

  final ObtenerCuentasUseCase _obtenerCuentas;
  final PagarServicioUseCase _pagar;
  final ConsultarEstadoPagoUseCase _consultarEstado;
  final List<Duration> _esperas;
  final String Function() _generarLlave;

  /// Invalida la validación de referencia (el comprobante ya no sirve).
  final void Function()? _alTerminar;

  bool _iniciado = false;
  bool _enVuelo = false;
  ProductoPago? _producto;
  Timer? _temporizador;
  int _generacionSondeo = 0;
  int _noEncontradas = 0;

  // ---------------------------------------------------------------- inicio

  /// Arranca el flujo. Para productos con referencia, [datosReferencia] es la
  /// validación vigente (con su comprobante).
  Future<void> iniciar(
    ProductoPago producto, {
    DatosPagoReferencia? datosReferencia,
  }) async {
    if (_iniciado) return;
    _iniciado = true;
    _producto = producto;
    state = PagoState(producto: producto);

    final esTelefono = producto.tipoFront == kTipoFrontTelefono;
    if (esTelefono) {
      final precio = producto.precio ?? 0;
      if (precio <= 0) return _cerrarConError(ErrorPago.generico);
    } else if (datosReferencia == null) {
      return _cerrarConError(ErrorPago.validacionExpirada);
    }

    final cuentas = await _cargarCuentas();
    if (!mounted) return;
    if (cuentas is Failure) {
      return _cerrarConError(
        cuentas.tipo == TipoFallo.prohibido
            ? ErrorPago.sinCuenta
            : ErrorPago.generico,
      );
    }
    final lista = (cuentas as Ok<List<CuentaCliente>>).data;
    if (lista.isEmpty) return _cerrarConError(ErrorPago.sinCuenta);

    if (esTelefono) {
      state = state.copiar(
        fase: FasePago.capturandoDatos,
        captura: CapturaPago.telefono,
        esReferencia: false,
        cuentas: lista,
        total: producto.precio,
      );
      return;
    }

    final datos = datosReferencia!;
    state = state.copiar(
      esReferencia: true,
      cuentas: lista,
      referencia: datos.referencia,
      idValidacion: datos.idValidacion,
    );
    if (datos.verificacion == VerificacionReferencia.soloFormato) {
      state = state.copiar(
        fase: FasePago.capturandoDatos,
        captura: CapturaPago.importe,
      );
      return;
    }
    final monto = datos.montoAPagar;
    if (monto == null || aCentavos(monto) <= 0) {
      return _cerrarConError(ErrorPago.generico);
    }
    _entrarAResumen(total: monto);
  }

  Future<Result<List<CuentaCliente>>> _cargarCuentas() async {
    try {
      return await _obtenerCuentas();
    } catch (_) {
      return const Failure(tipo: TipoFallo.desconocido, mensaje: '');
    }
  }

  /// Vuelve a empezar (solo productos con teléfono): llave y saldo nuevos.
  Future<void> reiniciar() async {
    final producto = _producto;
    if (producto == null) return;
    _cancelarSondeo();
    _iniciado = false;
    _enVuelo = false;
    await iniciar(producto);
  }

  // --------------------------------------------------------------- captura

  /// Teléfono de 10 dígitos capturado dos veces.
  void capturarTelefono(String telefono, String confirmacion) {
    if (state.fase != FasePago.capturandoDatos ||
        state.captura != CapturaPago.telefono) {
      return;
    }
    final error = validarTelefono(telefono, confirmacion);
    if (error != null) {
      state = state.copiar(errorTelefono: error);
      return;
    }
    state = state.copiar(errorTelefono: null, telefono: telefono.trim());
    _entrarAResumen(total: state.total ?? 0);
  }

  /// Importe del recibo (solo con verificación SOLO_FORMATO).
  void capturarImporte(String texto) {
    if (state.fase != FasePago.capturandoDatos ||
        state.captura != CapturaPago.importe) {
      return;
    }
    final r = validarImporte(texto);
    final monto = r.monto;
    if (monto == null) {
      state = state.copiar(errorImporte: r.error);
      return;
    }
    state = state.copiar(errorImporte: null, importe: monto);
    _entrarAResumen(total: monto);
  }

  void _entrarAResumen({required double total}) {
    state = state.copiar(
      fase: FasePago.resumen,
      captura: null,
      total: total,
      llave: _generarLlave(),
      saldoRechazado: false,
    );
  }

  /// Selector de cuenta (solo si hay varias). Como nada se ha enviado, la
  /// llave se renueva para esta nueva intención.
  void seleccionarCuenta(int indice) {
    if (state.fase != FasePago.resumen) return;
    if (indice < 0 || indice >= state.cuentas.length) return;
    state = state.copiar(
      cuentaIndex: indice,
      llave: _generarLlave(),
      saldoRechazado: false,
    );
  }

  // ---------------------------------------------------------- confirmación

  void pedirConfirmacion() {
    if (state.fase != FasePago.resumen || _enVuelo || !state.saldoSuficiente) {
      return;
    }
    state = state.copiar(
      fase: FasePago.confirmando,
      avisoPin: false,
      intentosRestantes: null,
      reintentoSinLlegar: false,
    );
  }

  /// Cancelar el diálogo del PIN: vuelve a `resumen` (o a `pendiente` si se
  /// abrió desde "Reintentar pago").
  void cancelarConfirmacion() {
    if (state.fase != FasePago.confirmando || _enVuelo) return;
    if (state.reintentoSinLlegar) {
      state = state.copiar(
        fase: FasePago.pendiente,
        avisoPin: false,
        reintentoSinLlegar: false,
      );
    } else {
      state = state.copiar(fase: FasePago.resumen, avisoPin: false);
    }
  }

  /// Envía el pago. Solo se acepta desde `confirmando` y sin otra petición en
  /// vuelo: el doble envío es imposible. El [pin] no se guarda en el estado.
  Future<void> confirmar(String pin) async {
    if (state.fase != FasePago.confirmando || _enVuelo) return;
    if (!pinValido(pin)) return;
    final cuenta = state.cuenta;
    final producto = state.producto;
    final total = state.total;
    final llave = state.llave;
    if (cuenta == null || producto == null || total == null || llave == null) {
      return;
    }
    _enVuelo = true;
    state = state.copiar(
      fase: FasePago.procesando,
      enVuelo: true,
      avisoPin: false,
    );

    final solicitud = SolicitudPago(
      numCuenta: cuenta.numCuenta,
      idSer: producto.idServicio,
      idProd: producto.idProducto,
      monto: total,
      esReferencia: state.esReferencia,
      telefono: state.esReferencia ? null : state.telefono,
      reference: state.esReferencia ? state.referencia : null,
      idValidacion: state.esReferencia ? state.idValidacion : null,
      twoFA: pin,
    );

    SalidaPago salida;
    try {
      salida = await _pagar(solicitud, idempotencyKey: llave);
    } catch (_) {
      // No se sabe si llegó: nunca una llave nueva.
      salida = const PagoPendiente(porRed: true);
    }
    _enVuelo = false;
    if (!mounted) return;
    await _aplicarSalida(salida);
  }

  Future<void> _aplicarSalida(SalidaPago salida) async {
    switch (salida) {
      case PagoConfirmado(:final comprobante):
        _terminarExitoso(comprobante);
      case PagoPendiente(:final comprobante, :final porRed):
        _aPendiente(comprobante, porRed: porRed);
      case PagoRechazado(:final motivo, :final comprobante):
        _terminar(
          state.sinDatosSensibles(
            fase: FasePago.rechazado,
            motivoRechazo: motivo,
            comprobante: comprobante,
          ),
        );
      case PagoPinIncorrecto(:final intentosRestantes):
        state = state.copiar(
          fase: FasePago.confirmando,
          enVuelo: false,
          avisoPin: true,
          intentosRestantes: intentosRestantes,
        );
      case PagoPinBloqueado():
        _cerrarConError(ErrorPago.pinBloqueado);
      case PagoValidacionExpirada():
        _cerrarConError(ErrorPago.validacionExpirada);
      case PagoSaldoInsuficiente():
        await _volverAResumenPorSaldo();
      case PagoSesionInvalida():
      case PagoErrorGenerico():
        _cerrarConError(ErrorPago.generico);
    }
  }

  Future<void> _volverAResumenPorSaldo() async {
    final seleccionada = state.cuenta?.numCuenta;
    state = state.copiar(
      fase: FasePago.resumen,
      enVuelo: false,
      saldoRechazado: true,
    );
    final r = await _cargarCuentas();
    if (!mounted || state.fase != FasePago.resumen) return;
    if (r is Ok<List<CuentaCliente>> && r.data.isNotEmpty) {
      var indice = r.data.indexWhere((c) => c.numCuenta == seleccionada);
      if (indice < 0) indice = 0;
      state = state.copiar(cuentas: r.data, cuentaIndex: indice);
    }
  }

  // ----------------------------------------------------- estados terminales

  void _terminarExitoso(ComprobantePago comprobante) {
    _terminar(
      state.sinDatosSensibles(
        fase: FasePago.exitoso,
        comprobante: comprobante,
        totalMostrado: comprobante.monto ?? state.total,
      ),
    );
  }

  void _cerrarConError(ErrorPago error) {
    _terminar(state.sinDatosSensibles(fase: FasePago.error, error: error));
  }

  /// Fin del flujo: borra todo dato sensible e invalida la validación.
  void _terminar(PagoState nuevo) {
    _cancelarSondeo();
    _enVuelo = false;
    state = nuevo;
    _alTerminar?.call();
  }

  // ------------------------------------------------------------- pendiente

  void _aPendiente(ComprobantePago? comprobante, {bool porRed = false}) {
    _noEncontradas = 0;
    state = state.copiar(
      fase: FasePago.pendiente,
      enVuelo: false,
      comprobante: comprobante,
      sinConexion: porRed,
      pagoNoLlego: false,
      reintentoSinLlegar: false,
      consultando: false,
    );
    _iniciarSondeo();
  }

  void _iniciarSondeo() {
    _cancelarSondeo();
    final generacion = ++_generacionSondeo;
    state = state.copiar(sondeando: true);
    _agendar(0, generacion);
  }

  void _agendar(int indice, int generacion) {
    if (!mounted || generacion != _generacionSondeo) return;
    if (indice >= _esperas.length) {
      state = state.copiar(sondeando: false);
      return;
    }
    _temporizador = Timer(_esperas[indice], () async {
      if (!mounted || generacion != _generacionSondeo) return;
      await _consultar(generacion);
      if (!mounted ||
          generacion != _generacionSondeo ||
          state.fase != FasePago.pendiente) {
        return;
      }
      _agendar(indice + 1, generacion);
    });
  }

  void _cancelarSondeo() {
    _generacionSondeo++;
    _temporizador?.cancel();
    _temporizador = null;
  }

  /// Una consulta de estado. Si resuelve el pago o debe detener el sondeo,
  /// invalida la generación para que no se agende otra.
  Future<void> _consultar(int generacion) async {
    if (state.consultando || state.fase != FasePago.pendiente) return;
    final upc = state.upc;
    final porLlave = upc == null;
    final llave = state.llave;
    if (porLlave && llave == null) return;
    state = state.copiar(consultando: true);

    SalidaConsulta salida;
    try {
      salida = await _consultarEstado(
        upc: upc,
        idempotencyKey: porLlave ? llave : null,
      );
    } catch (_) {
      salida = const ConsultaFallida();
    }
    if (!mounted || generacion != _generacionSondeo) return;
    if (state.fase != FasePago.pendiente) return;
    state = state.copiar(consultando: false);

    switch (salida) {
      case ConsultaConfirmada(:final comprobante):
        _terminarExitoso(comprobante);
      case ConsultaRechazada(:final motivo, :final comprobante):
        _terminar(
          state.sinDatosSensibles(
            fase: FasePago.rechazado,
            motivoRechazo: motivo,
            comprobante: comprobante,
          ),
        );
      case ConsultaSesionInvalida():
        _cancelarSondeo();
        state = state.copiar(sondeando: false);
      case ConsultaNoEncontrada():
        if (!porLlave) return;
        _noEncontradas++;
        if (_noEncontradas >= kMaximo404Seguidos) {
          _cancelarSondeo();
          state = state.copiar(sondeando: false, pagoNoLlego: true);
        }
      case ConsultaPendiente(:final comprobante):
        _noEncontradas = 0;
        if (comprobante != null) {
          state = state.copiar(comprobante: comprobante, sinConexion: false);
        }
      case ConsultaFallida():
        break;
    }
  }

  /// "Consultar de nuevo": una consulta inmediata y, si sigue pendiente,
  /// se reanuda el sondeo.
  Future<void> consultarDeNuevo() async {
    if (state.fase != FasePago.pendiente || state.consultando) return;
    _cancelarSondeo();
    final generacion = _generacionSondeo;
    state = state.copiar(pagoNoLlego: false);
    _noEncontradas = 0;
    await _consultar(generacion);
    if (!mounted || state.fase != FasePago.pendiente) return;
    if (generacion != _generacionSondeo || state.pagoNoLlego) return;
    _iniciarSondeo();
  }

  /// "Reintentar pago": pide el PIN otra vez y reutiliza LA MISMA llave.
  void reintentarPago() {
    if (state.fase != FasePago.pendiente || !state.pagoNoLlego || _enVuelo) {
      return;
    }
    _cancelarSondeo();
    state = state.copiar(
      fase: FasePago.confirmando,
      sondeando: false,
      avisoPin: false,
      intentosRestantes: null,
      reintentoSinLlegar: true,
    );
  }

  @override
  void dispose() {
    _cancelarSondeo();
    super.dispose();
  }
}
