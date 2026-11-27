import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/http/api_client.dart';
import '../../../core/http/api_providers.dart';
import '../../../core/http/json_helpers.dart';
import '../../../core/http/result.dart';

/// Cuenta recién creada (respuesta del registro).
class CuentaCreada {
  const CuentaCreada({
    required this.nombreCompleto,
    required this.username,
    required this.numeroCuenta,
    required this.saldoInicial,
  });

  final String nombreCompleto;
  final String username;
  final String numeroCuenta;
  final double saldoInicial;
}

/// Registro público de clientes (bandera 1) y catálogo de nacionalidades.
class RegistroRemoteDatasource {
  RegistroRemoteDatasource(this._api);

  final ApiClient _api;

  Future<Result<List<String>>> nacionalidades() {
    return _api.get<List<String>>(
      '/api/v1/cat/nacionalidades',
      autenticada: false,
      parser: (data) => [
        if (data is List)
          for (final n in data)
            if (n is Map && aString(n['nombre']) != null) aString(n['nombre'])!,
      ],
    );
  }

  Future<Result<CuentaCreada>> registrar(Map<String, Object?> solicitud) {
    return _api.post<CuentaCreada>(
      '/api/v1/layaway/cliente',
      body: solicitud,
      autenticada: false,
      receiveTimeout: const Duration(seconds: 30),
      parser: (data) {
        final m = data is Map ? data : const {};
        return CuentaCreada(
          nombreCompleto: aString(m['nombreCompleto']) ?? '',
          username: aString(m['username']) ?? '',
          numeroCuenta: aString(m['numeroCuenta']) ?? '',
          saldoInicial: aDouble(m['saldoInicial']) ?? 0,
        );
      },
    );
  }
}

final registroDatasourceProvider = Provider<RegistroRemoteDatasource>(
  (ref) => RegistroRemoteDatasource(ref.watch(apiClientProvider)),
);
