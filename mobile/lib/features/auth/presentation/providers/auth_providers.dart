import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/http/api_providers.dart';
import '../../../../core/session/session_controller.dart';
import '../../data/datasources/auth_remote_datasource.dart';
import '../../data/repositories/auth_repository_impl.dart';
import '../../domain/repositories/auth_repository.dart';
import '../../domain/usecases/cerrar_sesion_usecase.dart';
import '../../domain/usecases/iniciar_sesion_usecase.dart';

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepositoryImpl(
    AuthRemoteDataSource(ref.watch(apiClientProvider)),
  ),
);

final iniciarSesionUseCaseProvider = Provider<IniciarSesionUseCase>(
  (ref) => IniciarSesionUseCase(ref.watch(authRepositoryProvider)),
);

final cerrarSesionUseCaseProvider = Provider<CerrarSesionUseCase>(
  (ref) => CerrarSesionUseCase(ref.watch(authRepositoryProvider)),
);

/// Cierra sesión: llama al endpoint y, pase lo que pase, limpia la sesión local.
final cerrarSesionProvider = Provider<Future<void> Function()>((ref) {
  return () async {
    try {
      await ref.read(cerrarSesionUseCaseProvider)();
    } catch (_) {
      // Se ignora: el cierre local es obligatorio igualmente.
    }
    await ref.read(sessionControllerProvider.notifier).cerrarSesion();
  };
});
