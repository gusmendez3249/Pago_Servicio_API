import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/config/app_theme.dart';
import 'package:gesto_pago_mobile/features/auth/presentation/providers/auth_providers.dart';
import 'package:gesto_pago_mobile/features/auth/presentation/widgets/boton_cerrar_sesion.dart';

/// Monta el botón con un cierre de sesión espía.
Future<List<int>> _montar(WidgetTester tester) async {
  final llamadas = <int>[];
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        cerrarSesionProvider.overrideWithValue(() async => llamadas.add(1)),
      ],
      child: MaterialApp(
        theme: construirTema(),
        home: const Scaffold(body: Center(child: BotonCerrarSesion())),
      ),
    ),
  );
  return llamadas;
}

Future<void> _abrir(WidgetTester tester) async {
  await tester.tap(find.text('Cerrar sesión'));
  await tester.pumpAndSettle();
  expect(find.text('¿Cerrar sesión?'), findsOneWidget);
  expect(
    find.text('Tendrás que volver a iniciar sesión para pagar tus servicios.'),
    findsOneWidget,
  );
}

void main() {
  testWidgets('sin confirmar (Cancelar) no se cierra la sesión',
      (tester) async {
    final llamadas = await _montar(tester);
    await _abrir(tester);
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(find.text('¿Cerrar sesión?'), findsNothing);
    expect(llamadas, isEmpty);
  });

  testWidgets('tocar fuera del diálogo cancela y no cierra la sesión',
      (tester) async {
    final llamadas = await _montar(tester);
    await _abrir(tester);
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(find.text('¿Cerrar sesión?'), findsNothing);
    expect(llamadas, isEmpty);
  });

  testWidgets('el botón rojo confirma y cierra la sesión una sola vez',
      (tester) async {
    final llamadas = await _montar(tester);
    await _abrir(tester);
    final rojo = find.descendant(
      of: find.byType(DialogoCerrarSesion),
      matching: find.widgetWithText(FilledButton, 'Cerrar sesión'),
    );
    await tester.tap(rojo);
    await tester.pumpAndSettle();
    expect(llamadas, hasLength(1));
    expect(find.text('¿Cerrar sesión?'), findsNothing);
  });
}
