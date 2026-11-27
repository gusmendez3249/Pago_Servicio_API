import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/error/mensajes_error.dart';
import '../../../../core/http/result.dart';
import '../../../../core/session/session_controller.dart';
import '../../domain/entities/sesion_usuario.dart';
import 'auth_providers.dart';

enum EstadoLogin { inicial, cargando, exito, error }

class LoginState {
  const LoginState({
    this.estado = EstadoLogin.inicial,
    this.mensaje,
    this.erroresCampo = const {},
  });

  final EstadoLogin estado;
  final String? mensaje;

  /// Claves: `username` y `password`.
  final Map<String, String> erroresCampo;

  bool get cargando => estado == EstadoLogin.cargando;
}

final loginControllerProvider =
    NotifierProvider<LoginController, LoginState>(LoginController.new);

class LoginController extends Notifier<LoginState> {
  @override
  LoginState build() => const LoginState();

  void reiniciar() => state = const LoginState();

  void limpiarErrores() {
    if (state.estado == EstadoLogin.error || state.erroresCampo.isNotEmpty) {
      state = const LoginState();
    }
  }

  Future<void> iniciarSesion({
    required String username,
    required String password,
  }) async {
    if (state.cargando) return;
    state = const LoginState(estado: EstadoLogin.cargando);

    final Result<SesionUsuario> resultado =
        await ref.read(iniciarSesionUseCaseProvider)(
      username: username,
      password: password,
    );

    if (resultado is Failure) {
      state = _estadoDeFallo(resultado);
      return;
    }
    final sesion = (resultado as Ok<SesionUsuario>).data;
    state = const LoginState(estado: EstadoLogin.exito);
    await ref.read(sessionControllerProvider.notifier).iniciarSesion(
          token: sesion.token,
          username: sesion.username,
          nombreCliente: sesion.nombreCliente,
          rol: sesion.rol,
        );
  }

  LoginState _estadoDeFallo(Failure fallo) {
    // Texto amigable por campo: el detalle que envía el backend nunca se
    // muestra en pantalla.
    final errores = <String, String>{};
    for (final campo in fallo.campos) {
      final nombre = campo.campo.toLowerCase();
      if (nombre.contains('user')) {
        errores['username'] = 'Revisa tu usuario.';
      } else if (nombre.contains('pass')) {
        errores['password'] = 'Revisa tu contraseña.';
      }
    }
    return LoginState(
      estado: EstadoLogin.error,
      mensaje: mensajeDeFallo(fallo, esLogin: true),
      erroresCampo: errores,
    );
  }
}
