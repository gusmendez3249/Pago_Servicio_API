import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/http/result.dart';
import '../../../pago/domain/entities/cuenta_cliente.dart';
import '../../../pago/presentation/providers/pago_providers.dart';

/// Cuentas del cliente para la pantalla "Cuenta" (reutiliza el caso de uso
/// existente del pago). Solo lectura.
final cuentasClienteProvider =
    FutureProvider.autoDispose<Result<List<CuentaCliente>>>(
  (ref) => ref.watch(obtenerCuentasUseCaseProvider)(),
);
