import 'package:dio/dio.dart';

import '../http/api_client.dart';

/// Agrega `Authorization: Bearer <token>` cuando hay sesión.
class AuthInterceptor extends Interceptor {
  AuthInterceptor({required this.obtenerToken});

  final String? Function() obtenerToken;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final autenticada = options.extra[kExtraAutenticada] != false;
    final token = obtenerToken();
    if (autenticada && token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }
}
