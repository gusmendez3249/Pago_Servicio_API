import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/core/session/almacen_sesion.dart';
import 'package:gesto_pago_mobile/core/session/session_controller.dart';
import 'package:gesto_pago_mobile/features/historial/domain/entities/elemento_historial.dart';
import 'package:gesto_pago_mobile/features/historial/presentation/providers/historial_providers.dart';
import 'package:gesto_pago_mobile/features/historial/presentation/screens/detalle_historial_screen.dart';
import 'package:gesto_pago_mobile/features/historial/presentation/screens/historial_screen.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/comprobante_pago.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/providers/pago_providers.dart';
import 'package:gesto_pago_mobile/shared/widgets/barra_secciones.dart';

import '../pago/repositorio_falso.dart';
import 'historial_notifier_test.dart';

class _AlmacenEspia implements AlmacenSesion {
  int accesos = 0;
  @override
  Future<DatosSesionGuardada?> leer() async {
    accesos++;
    return null;
  }

  @override
  Future<void> guardar(DatosSesionGuardada datos) async => accesos++;
  @override
  Future<void> borrar() async => accesos++;
}

Future<_AlmacenEspia> _montar(
  WidgetTester tester,
  Widget pantalla, {
  RepoHistorialFalso? historial,
  RepoPagoFalso? pago,
}) async {
  final almacen = _AlmacenEspia();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        almacenSesionProvider.overrideWithValue(almacen),
        if (historial != null)
          historialRepositoryProvider.overrideWithValue(historial),
        if (pago != null) pagoRepositoryProvider.overrideWithValue(pago),
      ],
      child: MaterialApp(home: pantalla),
    ),
  );
  await tester.pumpAndSettle();
  return almacen;
}

void main() {
  group('lista', () {
    testWidgets('muestra filas con estado, folio e importe', (tester) async {
      // Pantalla alta: la lista es perezosa y los tres renglones deben verse.
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final repo = RepoHistorialFalso()
        ..responder = (_, p) => Ok(
              pag([
                const ElementoHistorial(
                  estatus: EstatusPago.confirmada,
                  upc: 'A1',
                  monto: 1234.5,
                  folio: 'F77',
                  servicio: 'Telcel',
                  producto: 'Recarga 100',
                  telefonoEnmascarado: '******5678',
                  fechaTransaccion: '2026-11-13T19:39:00',
                ),
                const ElementoHistorial(
                  estatus: EstatusPago.indeterminada,
                  upc: 'A2',
                  monto: 50,
                  identificadorOperacion: 'OP-5',
                  servicio: 'CFE',
                ),
                const ElementoHistorial(
                  estatus: EstatusPago.rechazada,
                  upc: 'A3',
                  servicio: 'Izzi',
                ),
              ], p),
            );
      final almacen = await _montar(
        tester,
        const HistorialScreen(),
        historial: repo,
      );
      // Título de la pantalla y destino activo de la barra inferior.
      expect(find.text('Mis pagos'), findsNWidgets(2));
      expect(
        find.descendant(
          of: find.byType(BarraSecciones),
          matching: find.text('Mis pagos'),
        ),
        findsOneWidget,
      );
      for (final t in ['Todos', 'Exitosos', 'Rechazados', 'Pendientes']) {
        expect(find.text(t), findsOneWidget);
      }
      expect(find.text('Exitoso'), findsOneWidget);
      expect(find.text('Rechazado'), findsWidgets);
      expect(find.text('Pendiente'), findsWidgets);
      // Movimientos agrupados por día: la fecha encabeza el grupo y la hora
      // va en el renglón.
      expect(find.text('13 nov 2026'), findsOneWidget);
      expect(find.text('19:39'), findsOneWidget);
      expect(find.text('Sin fecha'), findsOneWidget);
      expect(find.text('******5678'), findsOneWidget);
      expect(find.text('\$1,234.50'), findsOneWidget);
      expect(find.text('Folio F77'), findsOneWidget);
      expect(find.text('Número de operación OP-5'), findsOneWidget);
      expect(find.text('Ver más'), findsNothing);
      expect(almacen.accesos, 0);
    });

    testWidgets('vacío, con y sin filtro', (tester) async {
      await _montar(tester, const HistorialScreen(), historial: RepoHistorialFalso());
      expect(find.text('Aún no tienes pagos'), findsOneWidget);
      expect(find.text('Ir al catálogo'), findsOneWidget);
      await tester.tap(find.text('Exitosos'));
      await tester.pumpAndSettle();
      expect(find.text('No hay pagos con ese estado'), findsOneWidget);
    });

    testWidgets('error amigable y reintento', (tester) async {
      final repo = RepoHistorialFalso()
        ..responder = (_, __) => const Failure(
              tipo: TipoFallo.servidor,
              mensaje: 'NullPointerException interno',
              httpStatus: 500,
            );
      await _montar(tester, const HistorialScreen(), historial: repo);
      expect(
        find.text('No pudimos cargar tus pagos. Inténtalo de nuevo.'),
        findsOneWidget,
      );
      expect(find.textContaining('Exception'), findsNothing);
      expect(find.textContaining('500'), findsNothing);
      repo.responder = (_, p) => Ok(pag([el('A')], p));
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();
      expect(find.text('Exitoso'), findsOneWidget);
    });

    testWidgets('"Ver más" aparece con hayMas y acumula', (tester) async {
      final repo = RepoHistorialFalso()
        ..responder = (_, p) => p == 0
            ? Ok(pag([
                const ElementoHistorial(
                  estatus: EstatusPago.confirmada,
                  upc: 'A',
                  servicio: 'Telcel',
                ),
              ], 0, hayMas: true))
            : Ok(pag([
                const ElementoHistorial(
                  estatus: EstatusPago.rechazada,
                  upc: 'B',
                  servicio: 'CFE',
                ),
              ], 1));
      await _montar(tester, const HistorialScreen(), historial: repo);
      expect(find.text('Telcel'), findsOneWidget);
      await tester.tap(find.text('Ver más'));
      await tester.pumpAndSettle();
      expect(find.text('Telcel'), findsOneWidget);
      expect(find.text('CFE'), findsOneWidget);
      expect(find.text('Ver más'), findsNothing);
    });
  });

  group('detalle', () {
    ComprobantePago comp(EstatusPago e, {MotivoPago? motivo}) => ComprobantePago(
          estatus: e,
          upc: 'U1',
          motivo: motivo,
          monto: 100,
          folio: e == EstatusPago.confirmada ? 'F1' : null,
          identificadorOperacion: 'OP1',
          servicio: 'Telcel',
          producto: 'Recarga',
          telefonoEnmascarado: '******5678',
          numCuenta: '********1188',
          fechaTransaccion: '2026-11-13T19:39:00',
          pinServicio: e == EstatusPago.confirmada ? 'PIN-9' : null,
          leyenda: e == EstatusPago.confirmada ? '<b>Gracias</b>' : null,
        );

    testWidgets('encontrado: comprobante con PIN y sin animación', (tester) async {
      final pago = RepoPagoFalso()
        ..respuestasEstado = [ok200(comp(EstatusPago.confirmada))];
      final almacen = await _montar(
        tester,
        const DetalleHistorialScreen(upc: 'U1'),
        pago: pago,
      );
      expect(pago.consultas.single.upc, 'U1');
      expect(find.text('Comprobante'), findsOneWidget);
      expect(find.text('Código de tu tarjeta'), findsOneWidget);
      expect(find.text('PIN-9'), findsOneWidget);
      expect(find.text('<b>Gracias</b>'), findsOneWidget);
      expect(find.text('Volver a mis pagos'), findsOneWidget);
      expect(find.text('Consultar de nuevo'), findsNothing);
      expect(almacen.accesos, 0);
    });

    testWidgets('pendiente: permite consultar de nuevo', (tester) async {
      final pago = RepoPagoFalso()
        ..respuestasEstado = [
          ok200(comp(EstatusPago.pendiente)),
          ok200(comp(EstatusPago.confirmada)),
        ];
      await _montar(tester, const DetalleHistorialScreen(upc: 'U1'), pago: pago);
      expect(find.text('Consultar de nuevo'), findsOneWidget);
      expect(
        find.text(
          'Aún no recibimos la confirmación de esta operación. No vuelvas a pagar.',
        ),
        findsOneWidget,
      );
      await tester.ensureVisible(find.text('Consultar de nuevo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Consultar de nuevo'));
      await tester.pumpAndSettle();
      expect(pago.consultas, hasLength(2));
      expect(find.text('Consultar de nuevo'), findsNothing);
      expect(find.text('Exitoso'), findsOneWidget);
    });

    testWidgets('rechazado muestra el motivo amigable', (tester) async {
      final pago = RepoPagoFalso()
        ..respuestasEstado = [
          ok200(comp(EstatusPago.rechazada, motivo: MotivoPago.referenciaInvalida)),
        ];
      await _montar(tester, const DetalleHistorialScreen(upc: 'U1'), pago: pago);
      expect(
        find.text('No se reconoció la referencia. Revísala en tu recibo.'),
        findsOneWidget,
      );
    });

    testWidgets('404 muestra "No encontramos esa operación"', (tester) async {
      final pago = RepoPagoFalso()
        ..respuestasEstado = [
          fallo(404, tipo: TipoFallo.noEncontrado, motivo: MotivoPago.operacionNoEncontrada),
        ];
      await _montar(tester, const DetalleHistorialScreen(upc: 'U1'), pago: pago);
      expect(find.text('No encontramos esa operación'), findsOneWidget);
      expect(find.text('Volver a mis pagos'), findsOneWidget);
    });

    testWidgets('fallo genérico muestra texto amigable', (tester) async {
      final pago = RepoPagoFalso()..respuestasEstado = [fallo(500)];
      await _montar(tester, const DetalleHistorialScreen(upc: 'U1'), pago: pago);
      expect(
        find.text('No pudimos cargar la operación. Inténtalo de nuevo.'),
        findsOneWidget,
      );
      expect(find.textContaining('500'), findsNothing);
    });
  });
}
