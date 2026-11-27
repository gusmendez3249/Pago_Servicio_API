import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/screens/login_screen.dart';
import '../../features/catalogo/presentation/screens/catalogo_screen.dart';
import '../../features/catalogo/presentation/screens/detalle_producto_screen.dart';
import '../../features/catalogo/domain/entities/producto.dart';
import '../../features/catalogo/presentation/providers/catalogo_providers.dart';
import '../../features/cuenta/presentation/screens/cuenta_screen.dart';
import '../../features/historial/presentation/screens/detalle_historial_screen.dart';
import '../../features/historial/presentation/screens/historial_screen.dart';
import '../../features/pago/presentation/screens/pago_screen.dart';
import '../../features/referencia/domain/utils/guard_pago.dart';
import '../../features/referencia/presentation/providers/referencia_providers.dart';
import '../../features/referencia/presentation/screens/referencia_screen.dart';
import '../../features/registro/presentation/registro_screen.dart';
import '../session/session_controller.dart';

class _RefrescoRouter extends ChangeNotifier {
  void avisar() => notifyListeners();
}

abstract final class Rutas {
  static const login = '/login';
  static const registro = '/registro';
  static const catalogo = '/catalogo';
  static const historial = '/historial';
  static const cuenta = '/cuenta';

  /// Pantalla de inicio tras iniciar sesión: el catálogo.
  static const inicio = catalogo;

  static String detalleHistorial(String upc) =>
      '$historial/${Uri.encodeComponent(upc)}';

  static String detalleCatalogo(int idServicio, int idProducto) =>
      '$catalogo/$idServicio/$idProducto';

  static String referencia(int idServicio, int idProducto) =>
      '${detalleCatalogo(idServicio, idProducto)}/referencia';

  static String pago(int idServicio, int idProducto) =>
      '${detalleCatalogo(idServicio, idProducto)}/pago';
}

final routerProvider = Provider<GoRouter>((ref) {
  final refresco = _RefrescoRouter();
  ref.listen<bool>(
    sessionControllerProvider.select((s) => s.autenticada),
    (_, __) => refresco.avisar(),
  );

  final router = GoRouter(
    initialLocation: Rutas.inicio,
    refreshListenable: refresco,
    redirect: (context, state) {
      final autenticada = ref.read(sessionControllerProvider).autenticada;
      // Rutas públicas: iniciar sesión y crear cuenta
      final enLogin = state.matchedLocation == Rutas.login ||
          state.matchedLocation == Rutas.registro;
      if (!autenticada && !enLogin) return Rutas.login;
      if (autenticada && enLogin) return Rutas.inicio;
      return null;
    },
    routes: [
      GoRoute(path: '/', redirect: (_, __) => Rutas.inicio),
      GoRoute(
        path: Rutas.login,
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: Rutas.registro,
        builder: (context, state) => const RegistroScreen(),
      ),
      GoRoute(path: '/inicio', redirect: (_, __) => Rutas.catalogo),
      GoRoute(
        path: Rutas.catalogo,
        // Las secciones de la barra inferior cambian sin transición.
        pageBuilder: (context, state) => NoTransitionPage<void>(
          key: state.pageKey,
          child: const CatalogoScreen(),
        ),
        routes: [
          GoRoute(
            path: ':idServicio/:idProducto',
            builder: (context, state) => DetalleProductoScreen(
              idServicio: int.tryParse(
                state.pathParameters['idServicio'] ?? '',
              ),
              idProducto: int.tryParse(
                state.pathParameters['idProducto'] ?? '',
              ),
            ),
            routes: [
              GoRoute(
                path: 'referencia',
                builder: (context, state) => ReferenciaScreen(
                  idServicio: int.tryParse(
                    state.pathParameters['idServicio'] ?? '',
                  ),
                  idProducto: int.tryParse(
                    state.pathParameters['idProducto'] ?? '',
                  ),
                ),
              ),
              GoRoute(
                path: 'pago',
                // Candado `pagoPermitido`: teléfono con precio > 0, o una
                // validación de referencia vigente de este producto.
                redirect: (context, state) {
                  final idServicio = int.tryParse(
                    state.pathParameters['idServicio'] ?? '',
                  );
                  final idProducto = int.tryParse(
                    state.pathParameters['idProducto'] ?? '',
                  );
                  if (idServicio == null || idProducto == null) {
                    return Rutas.catalogo;
                  }
                  Producto? producto;
                  for (final p in ref.read(catalogoProvider).productos) {
                    if (p.idServicio == idServicio &&
                        p.idProducto == idProducto) {
                      producto = p;
                    }
                  }
                  final permitido = pagoPermitido(
                    tipoFront: producto?.tipoFront,
                    precio: producto?.precio,
                    datos: ref.read(referenciaProvider).datosPago,
                    idServicio: idServicio,
                    idProducto: idProducto,
                    ahora: ref.read(relojProvider)(),
                  );
                  if (permitido) return null;
                  return tipoFrontAdmiteReferencia(producto?.tipoFront)
                      ? Rutas.referencia(idServicio, idProducto)
                      : Rutas.detalleCatalogo(idServicio, idProducto);
                },
                builder: (context, state) => PagoScreen(
                  idServicio: int.tryParse(
                    state.pathParameters['idServicio'] ?? '',
                  ),
                  idProducto: int.tryParse(
                    state.pathParameters['idProducto'] ?? '',
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
      GoRoute(
        path: Rutas.historial,
        pageBuilder: (context, state) => NoTransitionPage<void>(
          key: state.pageKey,
          child: const HistorialScreen(),
        ),
        routes: [
          GoRoute(
            path: ':upc',
            builder: (context, state) =>
                DetalleHistorialScreen(upc: state.pathParameters['upc']),
          ),
        ],
      ),
      // Única ruta nueva: protegida por la misma redirección global.
      GoRoute(
        path: Rutas.cuenta,
        pageBuilder: (context, state) => NoTransitionPage<void>(
          key: state.pageKey,
          child: const CuentaScreen(),
        ),
      ),
    ],
  );

  ref.onDispose(() {
    router.dispose();
    refresco.dispose();
  });
  return router;
});
