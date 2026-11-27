import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/http/api_providers.dart';
import '../../../../core/http/result.dart';
import '../../../../core/session/session_controller.dart';
import '../../data/datasources/referencia_remote_datasource.dart';
import '../../data/repositories/referencia_repository_impl.dart';
import '../../domain/entities/validacion_referencia.dart';
import '../../domain/repositories/referencia_repository.dart';
import '../../domain/usecases/validar_referencia_usecase.dart';
import '../../domain/utils/formato_referencia.dart';

/// Máquina de estados de la captura y validación de referencia.
enum EstadoReferencia {
  capturando,
  formatoInvalido,
  validando,
  valida,
  soloConsulta,
  invalida,
  error,
  expirada,
}

class ReferenciaState {
  const ReferenciaState({
    this.estado = EstadoReferencia.capturando,
    this.idSer,
    this.idProd,
    this.referencia = '',
    this.errorFormato,
    this.validacion,
    this.expiraEn,
  });

  final EstadoReferencia estado;

  /// Producto al que pertenece este estado (null = sin flujo activo).
  final int? idSer;
  final int? idProd;

  /// Texto tal como lo escribió la persona (sin recortar).
  final String referencia;
  final ErrorFormatoReferencia? errorFormato;

  /// Respuesta vigente; solo en `valida` y `soloConsulta`.
  final ValidacionReferencia? validacion;
  final DateTime? expiraEn;

  bool perteneceA(int idServicio, int idProducto) =>
      idSer == idServicio && idProd == idProducto;

  /// "Continuar al pago" solo en `valida` con `idValidacion` no nulo.
  bool get puedeContinuar =>
      estado == EstadoReferencia.valida && datosPago != null;

  /// Datos para el Sprint 3; null salvo en `valida` con comprobante.
  DatosPagoReferencia? get datosPago {
    final v = validacion;
    final vence = expiraEn;
    final saldo = v?.saldo;
    final situacion = v?.situacionEfectiva;
    final id = v?.idValidacion;
    final ids = idSer;
    final idp = idProd;
    final soloFormato = v?.verificacion == VerificacionReferencia.soloFormato;
    if (estado != EstadoReferencia.valida ||
        v == null ||
        vence == null ||
        (!soloFormato && (saldo == null || situacion == null)) ||
        id == null ||
        id.isEmpty ||
        ids == null ||
        idp == null) {
      return null;
    }
    return DatosPagoReferencia(
      idSer: ids,
      idProd: idp,
      referencia: recortarReferencia(referencia),
      idValidacion: id,
      saldo: saldo,
      situacion: situacion,
      servicio: v.servicio ?? '',
      producto: v.producto ?? '',
      expiraEn: vence,
      verificacion: v.verificacion,
      montoAPagar: soloFormato ? null : (v.montoAPagar ?? saldo),
    );
  }
}

/// Estado en memoria (nunca se persiste). Lo leen la pantalla y el guard de la
/// ruta de pago.
class ReferenciaNotifier extends StateNotifier<ReferenciaState> {
  ReferenciaNotifier(
    this._validar, {
    DateTime Function()? reloj,
    Duration? vigenciaFija,
  })  : _reloj = reloj ?? DateTime.now,
        _vigenciaFija = vigenciaFija,
        super(const ReferenciaState());

  final ValidarReferenciaUseCase _validar;
  final DateTime Function() _reloj;

  /// Sustituye a `vigenciaSegundos` (solo para pruebas).
  final Duration? _vigenciaFija;

  Timer? _temporizador;

  /// Identifica la validación en curso; las respuestas viejas se descartan.
  int _generacion = 0;

  /// Entra al flujo de un producto. Si es otro distinto del actual, descarta
  /// cualquier validación previa.
  void iniciar(int idSer, int idProd) {
    if (state.perteneceA(idSer, idProd)) return;
    _reiniciar(ReferenciaState(idSer: idSer, idProd: idProd));
  }

  /// Sale del flujo de ese producto (si sigue siendo el activo).
  void salir(int idSer, int idProd) {
    if (!mounted || !state.perteneceA(idSer, idProd)) return;
    limpiar();
  }

  /// Descarta todo (cierre de sesión, 401, cambio de producto).
  void limpiar() => _reiniciar(const ReferenciaState());

  /// Invalida la validación vigente (y su comprobante) conservando el texto
  /// escrito. Se usa al salir de la pantalla de pago sin terminar.
  void invalidarValidacion() {
    if (!mounted) return;
    editar(state.referencia);
  }

  /// La persona editó el texto: se invalida la validación previa.
  void editar(String texto) {
    if (state.estado == EstadoReferencia.validando) return;
    if (texto == state.referencia &&
        state.estado == EstadoReferencia.capturando) {
      return;
    }
    _cancelarTemporizador();
    _generacion++;
    state = ReferenciaState(
      idSer: state.idSer,
      idProd: state.idProd,
      referencia: texto,
    );
  }

  /// Si la validación vigente ya venció, pasa a `expirada`.
  void verificarVigencia() {
    if (state.estado != EstadoReferencia.valida) return;
    final vence = state.expiraEn;
    if (vence == null || !_reloj().isBefore(vence)) _expirar();
  }

  Future<void> reintentar() => validar();

  Future<void> validar() async {
    if (state.estado == EstadoReferencia.validando) return;
    final idSer = state.idSer;
    final idProd = state.idProd;
    if (idSer == null || idProd == null) return;

    final texto = state.referencia;
    final errorFormato = validarFormatoReferencia(texto);
    if (errorFormato != null) {
      _cancelarTemporizador();
      _generacion++;
      state = ReferenciaState(
        estado: EstadoReferencia.formatoInvalido,
        idSer: idSer,
        idProd: idProd,
        referencia: texto,
        errorFormato: errorFormato,
      );
      return;
    }

    _cancelarTemporizador();
    final miGeneracion = ++_generacion;
    state = ReferenciaState(
      estado: EstadoReferencia.validando,
      idSer: idSer,
      idProd: idProd,
      referencia: texto,
    );

    Result<ValidacionReferencia> resultado;
    try {
      resultado = await _validar(
        idSer: idSer,
        idProd: idProd,
        referencia: texto,
      );
    } catch (_) {
      resultado = const Failure(tipo: TipoFallo.desconocido, mensaje: '');
    }
    if (!mounted || miGeneracion != _generacion) return;

    if (resultado is Ok<ValidacionReferencia>) {
      _aplicarRespuesta(resultado.data, idSer, idProd, texto);
    } else if (resultado is Failure) {
      if (resultado.tipo == TipoFallo.noAutenticado) {
        // El interceptor cierra la sesión; aquí solo se descarta el estado.
        limpiar();
        return;
      }
      _poner(EstadoReferencia.error, idSer, idProd, texto);
    }
  }

  void _aplicarRespuesta(
    ValidacionReferencia r,
    int idSer,
    int idProd,
    String texto,
  ) {
    switch (r.estado) {
      case EstadoValidacion.invalida:
        _poner(EstadoReferencia.invalida, idSer, idProd, texto);
      case EstadoValidacion.desconocido:
        _poner(EstadoReferencia.error, idSer, idProd, texto);
      case EstadoValidacion.valida:
        final esSoloFormato =
            r.verificacion == VerificacionReferencia.soloFormato;
        if (!esSoloFormato &&
            (r.saldo == null || r.situacionEfectiva == null)) {
          _poner(EstadoReferencia.error, idSer, idProd, texto);
        } else if (!r.puedePagar) {
          state = ReferenciaState(
            estado: EstadoReferencia.soloConsulta,
            idSer: idSer,
            idProd: idProd,
            referencia: texto,
            validacion: r,
          );
        } else {
          _aplicarValida(r, idSer, idProd, texto);
        }
    }
  }

  void _aplicarValida(
    ValidacionReferencia r,
    int idSer,
    int idProd,
    String texto,
  ) {
    final segundos = r.vigenciaSegundos;
    final Duration? vigencia = _vigenciaFija ??
        ((segundos == null || segundos <= 0)
            ? null
            : Duration(seconds: segundos));
    // Puede pagar pero sin comprobante o sin vigencia: no hay forma segura de
    // continuar.
    if (r.idValidacion == null || vigencia == null) {
      _poner(EstadoReferencia.error, idSer, idProd, texto);
      return;
    }
    state = ReferenciaState(
      estado: EstadoReferencia.valida,
      idSer: idSer,
      idProd: idProd,
      referencia: texto,
      validacion: r,
      expiraEn: _reloj().add(vigencia),
    );
    _temporizador = Timer(vigencia, _expirar);
  }

  void _expirar() {
    if (!mounted || state.estado != EstadoReferencia.valida) return;
    _cancelarTemporizador();
    _generacion++;
    state = ReferenciaState(
      estado: EstadoReferencia.expirada,
      idSer: state.idSer,
      idProd: state.idProd,
      referencia: state.referencia,
    );
  }

  void _poner(EstadoReferencia estado, int idSer, int idProd, String texto) {
    state = ReferenciaState(
      estado: estado,
      idSer: idSer,
      idProd: idProd,
      referencia: texto,
    );
  }

  void _reiniciar(ReferenciaState nuevo) {
    _cancelarTemporizador();
    _generacion++;
    state = nuevo;
  }

  void _cancelarTemporizador() {
    _temporizador?.cancel();
    _temporizador = null;
  }

  @override
  void dispose() {
    _cancelarTemporizador();
    super.dispose();
  }
}

final referenciaRepositoryProvider = Provider<ReferenciaRepository>(
  (ref) => ReferenciaRepositoryImpl(
    ReferenciaRemoteDataSource(ref.watch(apiClientProvider)),
  ),
);

final validarReferenciaUseCaseProvider = Provider<ValidarReferenciaUseCase>(
  (ref) => ValidarReferenciaUseCase(ref.watch(referenciaRepositoryProvider)),
);

/// Vigencia fija para pruebas; null usa `vigenciaSegundos` de la respuesta.
final vigenciaReferenciaProvider = Provider<Duration?>((ref) => null);

/// Estado de validación en memoria. No es autoDispose: el guard de la ruta de
/// pago (y el Sprint 3) lo leen aunque la pantalla de captura no esté montada.
final referenciaProvider =
    StateNotifierProvider<ReferenciaNotifier, ReferenciaState>((ref) {
  final notifier = ReferenciaNotifier(
    ref.watch(validarReferenciaUseCaseProvider),
    reloj: ref.read(relojProvider),
    vigenciaFija: ref.watch(vigenciaReferenciaProvider),
  );
  // Cierre de sesión, 401 o expiración por inactividad: se descarta todo.
  ref.listen<bool>(
    sessionControllerProvider.select((s) => s.autenticada),
    (anterior, actual) {
      if (!actual) notifier.limpiar();
    },
  );
  return notifier;
});
