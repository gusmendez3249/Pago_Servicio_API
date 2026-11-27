import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/app_config.dart';
import '../interceptors/auth_interceptor.dart';
import '../interceptors/sesion_interceptor.dart';
import '../session/session_controller.dart';
import 'api_client.dart';

final apiBaseUrlProvider = Provider<String>((ref) => AppConfig.apiBaseUrl);

final dioProvider = Provider<Dio>((ref) {
  final dio = Dio(opcionesBaseDio(ref.watch(apiBaseUrlProvider)));
  dio.interceptors.addAll([
    AuthInterceptor(
      obtenerToken: () => ref.read(sessionControllerProvider).token,
    ),
    SesionInterceptor(
      alActividad: () =>
          ref.read(sessionControllerProvider.notifier).registrarActividad(),
      alNoAutorizado: () => ref
          .read(sessionControllerProvider.notifier)
          .invalidarPorNoAutorizado(),
      alCuentaInactiva: () => ref
          .read(sessionControllerProvider.notifier)
          .invalidarPorCuentaInactiva(),
    ),
  ]);
  ref.onDispose(dio.close);
  return dio;
});

final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient(ref.watch(dioProvider)),
);
