import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/http/api_providers.dart';
import '../../../referencia/presentation/providers/referencia_providers.dart';
import '../../data/datasources/pago_remote_datasource.dart';
import '../../data/repositories/pago_repository_impl.dart';
import '../../domain/repositories/pago_repository.dart';
import '../../domain/usecases/pago_usecases.dart';
import '../../domain/utils/reglas_pago.dart';
import 'pago_notifier.dart';
import 'pago_state.dart';

final pagoRepositoryProvider = Provider<PagoRepository>(
  (ref) =>
      PagoRepositoryImpl(PagoRemoteDataSource(ref.watch(apiClientProvider))),
);

final obtenerCuentasUseCaseProvider = Provider<ObtenerCuentasUseCase>(
  (ref) => ObtenerCuentasUseCase(ref.watch(pagoRepositoryProvider)),
);

final pagarServicioUseCaseProvider = Provider<PagarServicioUseCase>(
  (ref) => PagarServicioUseCase(ref.watch(pagoRepositoryProvider)),
);

final consultarEstadoPagoUseCaseProvider = Provider<ConsultarEstadoPagoUseCase>(
  (ref) => ConsultarEstadoPagoUseCase(ref.watch(pagoRepositoryProvider)),
);

/// Instantes (desde que queda pendiente) de cada consulta de estado.
/// Inyectable para pruebas.
final intervalosSondeoProvider = Provider<List<Duration>>(
  (ref) => kIntervalosSondeo,
);

/// Estado del pago en memoria: se descarta al salir de la pantalla.
final pagoProvider = StateNotifierProvider.autoDispose
    .family<PagoNotifier, PagoState, ClavePago>((ref, clave) {
  return PagoNotifier(
    obtenerCuentas: ref.watch(obtenerCuentasUseCaseProvider),
    pagar: ref.watch(pagarServicioUseCaseProvider),
    consultarEstado: ref.watch(consultarEstadoPagoUseCaseProvider),
    intervalosSondeo: ref.watch(intervalosSondeoProvider),
    alTerminar: () => ref.read(referenciaProvider.notifier).limpiar(),
  );
});
