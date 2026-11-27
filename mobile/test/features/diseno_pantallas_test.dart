import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/config/app_theme.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/core/session/almacen_sesion.dart';
import 'package:gesto_pago_mobile/core/session/session_controller.dart';
import 'package:gesto_pago_mobile/features/auth/presentation/screens/login_screen.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/entities/producto.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/repositories/catalogo_repository.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/usecases/obtener_catalogo_usecase.dart';
import 'package:gesto_pago_mobile/features/catalogo/presentation/providers/catalogo_providers.dart';
import 'package:gesto_pago_mobile/features/catalogo/presentation/screens/catalogo_screen.dart';
import 'package:gesto_pago_mobile/features/catalogo/presentation/screens/detalle_producto_screen.dart';
import 'package:gesto_pago_mobile/features/cuenta/presentation/screens/cuenta_screen.dart';
import 'package:gesto_pago_mobile/features/historial/domain/entities/elemento_historial.dart';
import 'package:gesto_pago_mobile/features/historial/presentation/providers/historial_providers.dart';
import 'package:gesto_pago_mobile/features/historial/presentation/screens/detalle_historial_screen.dart';
import 'package:gesto_pago_mobile/features/historial/presentation/screens/historial_screen.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/comprobante_pago.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/providers/pago_providers.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/screens/pago_screen.dart';

import 'historial/historial_notifier_test.dart';
import 'pago/repositorio_falso.dart';

/// Prueba de diseño: las pantallas principales se montan a 360 px de ancho
/// con el texto del sistema al 130 % sin desbordes (un desborde hace fallar
/// la prueba) y con objetivos táctiles de al menos 48 px.

class _Catalogo implements CatalogoRepository {
  @override
  Future<Result<List<Producto>>> obtenerProductos() async => const Ok([
        Producto(
          servicio: 'Compañía de Telefonía Celular del Centro',
          producto: 'Recarga de tiempo aire de 100 pesos',
          idServicio: 1,
          idProducto: 2,
          tipoFront: 1,
          precio: 100,
          categoria: 'Recargas',
          tipoReferencia: 'Número telefónico a 10 dígitos',
          hasDigitoVerificador: false,
        ),
        Producto(
          servicio: 'Luz',
          producto: 'Recibo bimestral',
          idServicio: 3,
          idProducto: 4,
          tipoFront: 2,
          precio: 12.5,
          categoria: 'Electricidad',
        ),
      ]);
}

class _Almacen implements AlmacenSesion {
  @override
  Future<DatosSesionGuardada?> leer() async => null;
  @override
  Future<void> guardar(DatosSesionGuardada datos) async {}
  @override
  Future<void> borrar() async {}
}

Future<void> _montar(WidgetTester tester, Widget pantalla) async {
  tester.view.physicalSize = const Size(360, 740);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final repoHistorial = RepoHistorialFalso()
    ..responder = (_, p) => Ok(
          pag([
            const ElementoHistorial(
              estatus: EstatusPago.confirmada,
              upc: 'A1',
              monto: 12345.5,
              folio: 'F7788990011',
              servicio: 'Compañía de Telefonía Celular del Centro',
              producto: 'Recarga de tiempo aire de 100 pesos',
              telefonoEnmascarado: '******5678',
              fechaTransaccion: '2026-11-13T19:39:00',
            ),
            const ElementoHistorial(
              estatus: EstatusPago.indeterminada,
              upc: 'A2',
              monto: 50,
              identificadorOperacion: 'OP-5',
              servicio: 'Agua',
            ),
          ], p),
        );
  final repoPago = RepoPagoFalso()
    ..respuestasEstado = [
      ok200(
        comprobante(
          EstatusPago.confirmada,
          folio: 'F1234567890',
          pinServicio: 'ABCD-1234-EFGH',
          leyenda: 'Gracias por tu pago.',
        ),
      ),
    ];
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        almacenSesionProvider.overrideWithValue(_Almacen()),
        obtenerCatalogoUseCaseProvider.overrideWithValue(
          ObtenerCatalogoUseCase(_Catalogo()),
        ),
        historialRepositoryProvider.overrideWithValue(repoHistorial),
        pagoRepositoryProvider.overrideWithValue(repoPago),
      ],
      child: MaterialApp(
        theme: construirTema(),
        builder: (context, hijo) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: const TextScaler.linear(1.3),
          ),
          child: hijo!,
        ),
        home: pantalla,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('inicio de sesión', (tester) async {
    await _montar(tester, const LoginScreen());
    expect(find.text('Inicia sesión'), findsOneWidget);
    expect(tester.getSize(find.byType(FilledButton)).height,
        greaterThanOrEqualTo(48));
  });

  testWidgets('catálogo con accesos por categoría', (tester) async {
    await _montar(tester, const CatalogoScreen());
    // Igual que la web: saludo y buscador, sin subtítulo.
    expect(find.text('Hola'), findsOneWidget);
    expect(find.text('Buscar servicio o producto'), findsOneWidget);
    expect(find.text('¿Qué vas a pagar hoy?'), findsNothing);
    expect(find.text('Electricidad'), findsWidgets);
    expect(find.text('Todos los servicios'), findsOneWidget);
  });

  testWidgets('detalle del producto con el botón anclado', (tester) async {
    await _montar(
      tester,
      const DetalleProductoScreen(idServicio: 1, idProducto: 2),
    );
    expect(find.text('Continuar'), findsOneWidget);
    // Igual que la web: "Cancelar" en la barra del flujo y el aviso del
    // siguiente paso como primera tarjeta.
    expect(find.text('Cancelar'), findsOneWidget);
    // Sin datos internos: ni identificadores ni el código del tipo de
    // referencia; solo la categoría y el dígito verificador.
    expect(find.text('Categoría'), findsOneWidget);
    expect(find.text('Dígito verificador'), findsOneWidget);
    expect(find.text('Tipo de referencia'), findsNothing);
    expect(find.text('Número telefónico a 10 dígitos'), findsNothing);
    expect(find.text('Servicio'), findsNothing);
    expect(find.text('Producto'), findsNothing);
    expect(
      find.text('En el siguiente paso capturas el número de teléfono.'),
      findsOneWidget,
    );
  });

  testWidgets('pago: captura, resumen y PIN en hoja inferior', (tester) async {
    await _montar(tester, const PagoScreen(idServicio: 1, idProducto: 2));
    final campos = find.byType(TextField);
    await tester.enterText(campos.at(0), '5512345678');
    await tester.enterText(campos.at(1), '5512345678');
    await tester.tap(find.text('Revisar pago'));
    await tester.pumpAndSettle();
    expect(find.text('Revisa y confirma'), findsOneWidget);
    await tester.tap(find.text('Pagar \$100.00'));
    await tester.pumpAndSettle();
    expect(find.byType(BottomSheet), findsOneWidget);
    expect(find.text('Confirmar pago'), findsOneWidget);
  });

  testWidgets('historial agrupado y detalle', (tester) async {
    await _montar(tester, const HistorialScreen());
    expect(find.text('13 nov 2026'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    await _montar(tester, const DetalleHistorialScreen(upc: 'U1'));
    expect(find.text('F1234567890'), findsOneWidget);
    expect(find.text('ABCD-1234-EFGH'), findsOneWidget);
  });

  testWidgets('cuenta: cerrar sesión pide confirmación', (tester) async {
    await _montar(tester, const CuentaScreen());
    expect(find.text('Cuentas de cargo'), findsOneWidget);
    await tester.ensureVisible(find.text('Cerrar sesión'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cerrar sesión'));
    await tester.pumpAndSettle();
    // Diálogo de confirmación, sin desbordes con el texto al 130 %.
    expect(find.text('¿Cerrar sesión?'), findsOneWidget);
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(find.text('¿Cerrar sesión?'), findsNothing);
  });
}
