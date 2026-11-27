import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../pago/domain/entities/comprobante_pago.dart';
import '../../../pago/domain/entities/salida_pago.dart';
import '../../../pago/domain/usecases/pago_usecases.dart';

enum FaseDetalle { cargando, listo, noEncontrado, error }

class DetalleHistorialState {
  const DetalleHistorialState({
    this.fase = FaseDetalle.cargando,
    this.comprobante,
    this.consultando = false,
  });

  final FaseDetalle fase;
  final ComprobantePago? comprobante;

  /// "Consultar de nuevo" en curso.
  final bool consultando;

  /// Sigue sin confirmarse ni rechazarse.
  bool get pendiente {
    final c = comprobante;
    return c != null &&
        c.estatus != EstatusPago.confirmada &&
        c.estatus != EstatusPago.rechazada;
  }
}

/// Detalle de una operación: `POST /api/v1/pagos/estado {upc}`.
class DetalleHistorialNotifier extends StateNotifier<DetalleHistorialState> {
  DetalleHistorialNotifier(this._consultar, this._upc)
      : super(const DetalleHistorialState()) {
    cargar();
  }

  final ConsultarEstadoPagoUseCase _consultar;
  final String _upc;

  Future<void> cargar() async {
    state = const DetalleHistorialState();
    await _consultarYAplicar(conservar: false);
  }

  /// Vuelve a preguntar por una operación pendiente; si falla conserva lo
  /// que ya se mostraba.
  Future<void> consultarDeNuevo() async {
    if (state.consultando) return;
    state = DetalleHistorialState(
      fase: state.fase,
      comprobante: state.comprobante,
      consultando: true,
    );
    await _consultarYAplicar(conservar: true);
  }

  Future<void> _consultarYAplicar({required bool conservar}) async {
    SalidaConsulta salida;
    try {
      salida = await _consultar(upc: _upc);
    } catch (_) {
      salida = const ConsultaFallida();
    }
    if (!mounted) return;
    final anterior = state;
    final ComprobantePago? comprobante = switch (salida) {
      ConsultaConfirmada(:final comprobante) => comprobante,
      ConsultaRechazada(:final comprobante) => comprobante,
      ConsultaPendiente(:final comprobante) => comprobante,
      _ => null,
    };
    if (comprobante != null) {
      state = DetalleHistorialState(
        fase: FaseDetalle.listo,
        comprobante: comprobante,
      );
    } else if (salida is ConsultaNoEncontrada) {
      state = const DetalleHistorialState(fase: FaseDetalle.noEncontrado);
    } else if (conservar && anterior.comprobante != null) {
      state = DetalleHistorialState(
        fase: anterior.fase,
        comprobante: anterior.comprobante,
      );
    } else {
      state = const DetalleHistorialState(fase: FaseDetalle.error);
    }
  }
}
