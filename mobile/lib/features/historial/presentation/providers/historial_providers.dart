import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/http/api_providers.dart';
import '../../../../core/session/session_controller.dart';
import '../../../pago/presentation/providers/pago_providers.dart';
import '../../data/datasources/historial_remote_datasource.dart';
import '../../data/repositories/historial_repository_impl.dart';
import '../../domain/repositories/historial_repository.dart';
import '../../domain/usecases/obtener_historial_usecase.dart';
import 'detalle_historial_notifier.dart';
import 'historial_notifier.dart';

final historialRepositoryProvider = Provider<HistorialRepository>(
  (ref) => HistorialRepositoryImpl(
    HistorialRemoteDataSource(ref.watch(apiClientProvider)),
  ),
);

final obtenerHistorialUseCaseProvider = Provider<ObtenerHistorialUseCase>(
  (ref) => ObtenerHistorialUseCase(ref.watch(historialRepositoryProvider)),
);

/// La lista se pide al servidor cada vez que se entra (el estado se descarta
/// al salir) y se limpia al cerrar sesión o ante un 401.
final historialProvider =
    StateNotifierProvider.autoDispose<HistorialNotifier, HistorialState>((ref) {
  final notifier =
      HistorialNotifier(ref.watch(obtenerHistorialUseCaseProvider));
  ref.listen<bool>(
    sessionControllerProvider.select((s) => s.autenticada),
    (_, actual) {
      if (!actual) notifier.limpiar();
    },
  );
  return notifier;
});

/// Detalle de una operación, por `upc`; se descarta al salir.
final detalleHistorialProvider = StateNotifierProvider.autoDispose
    .family<DetalleHistorialNotifier, DetalleHistorialState, String>(
  (ref, upc) => DetalleHistorialNotifier(
    ref.watch(consultarEstadoPagoUseCaseProvider),
    upc,
  ),
);
