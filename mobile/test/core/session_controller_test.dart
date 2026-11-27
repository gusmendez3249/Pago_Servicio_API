import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/config/app_config.dart';
import 'package:gesto_pago_mobile/core/session/almacen_sesion.dart';
import 'package:gesto_pago_mobile/core/session/rol_usuario.dart';
import 'package:gesto_pago_mobile/core/session/session_controller.dart';

class AlmacenEnMemoria implements AlmacenSesion {
  DatosSesionGuardada? datos;

  @override
  Future<DatosSesionGuardada?> leer() async => datos;

  @override
  Future<void> guardar(DatosSesionGuardada d) async => datos = d;

  @override
  Future<void> borrar() async => datos = null;
}

void main() {
  late AlmacenEnMemoria almacen;
  late DateTime ahora;
  late ProviderContainer contenedor;

  setUp(() {
    almacen = AlmacenEnMemoria();
    ahora = DateTime(2026, 1, 1, 12);
    contenedor = ProviderContainer(
      overrides: [
        almacenSesionProvider.overrideWithValue(almacen),
        relojProvider.overrideWithValue(() => ahora),
      ],
    );
    addTearDown(contenedor.dispose);
  });

  Future<SessionController> iniciar() async {
    final ctrl = contenedor.read(sessionControllerProvider.notifier);
    await ctrl.iniciarSesion(
      token: 'token-de-prueba',
      username: 'usuario.prueba',
      nombreCliente: 'Cliente Prueba',
      rol: RolUsuario.cliente,
    );
    return ctrl;
  }

  test('iniciarSesion guarda estado y almacen', () async {
    await iniciar();
    final estado = contenedor.read(sessionControllerProvider);
    expect(estado.autenticada, isTrue);
    expect(estado.rol, RolUsuario.cliente);
    expect(almacen.datos?.token, 'token-de-prueba');
  });

  test('cerrarSesion borra estado y almacen', () async {
    final ctrl = await iniciar();
    await ctrl.cerrarSesion();
    final estado = contenedor.read(sessionControllerProvider);
    expect(estado.autenticada, isFalse);
    expect(estado.token, isNull);
    expect(estado.username, isNull);
    expect(almacen.datos, isNull);
  });

  test('volver de segundo plano tras 5 min cierra sesion con aviso', () async {
    final ctrl = await iniciar();
    ahora = ahora.add(AppConfig.inactividadMaxima + const Duration(seconds: 1));
    await ctrl.alVolverDeSegundoPlano();
    final estado = contenedor.read(sessionControllerProvider);
    expect(estado.autenticada, isFalse);
    expect(estado.avisoSesion, 'Tu sesión expiró por inactividad');
    expect(almacen.datos, isNull);
  });

  test('volver de segundo plano antes de 5 min mantiene la sesion', () async {
    final ctrl = await iniciar();
    ahora = ahora.add(const Duration(minutes: 2));
    await ctrl.alVolverDeSegundoPlano();
    expect(contenedor.read(sessionControllerProvider).autenticada, isTrue);
  });

  test('restaurar descarta una sesion vencida', () async {
    almacen.datos = DatosSesionGuardada(
      token: 'viejo',
      username: 'u',
      nombreCliente: 'n',
      rol: 'CLIENTE',
      ultimaActividad: ahora.subtract(const Duration(minutes: 10)),
    );
    await contenedor.read(sessionControllerProvider.notifier).restaurar();
    expect(contenedor.read(sessionControllerProvider).autenticada, isFalse);
    expect(almacen.datos, isNull);
  });
}
