import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/core/session/almacen_sesion.dart';
import 'package:gesto_pago_mobile/core/session/session_controller.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/entities/producto.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/repositories/catalogo_repository.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/usecases/obtener_catalogo_usecase.dart';
import 'package:gesto_pago_mobile/features/catalogo/presentation/providers/catalogo_providers.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/comprobante_pago.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/providers/pago_providers.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/screens/pago_screen.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/widgets/comprobante_vista.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/widgets/dialogo_pin.dart';

import 'repositorio_falso.dart';

class _CatalogoFalso implements CatalogoRepository {
  @override
  Future<Result<List<Producto>>> obtenerProductos() async => const Ok([
        Producto(
          servicio: 'Telcel',
          producto: 'Recarga 100',
          idServicio: 1,
          idProducto: 2,
          tipoFront: 1,
          precio: 100,
        ),
      ]);
}

/// Almacenamiento que cuenta cualquier acceso: el pago no debe tocarlo.
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

Future<({RepoPagoFalso repo, _AlmacenEspia almacen})> _montar(
  WidgetTester tester,
  RepoPagoFalso repo,
) async {
  final almacen = _AlmacenEspia();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        pagoRepositoryProvider.overrideWithValue(repo),
        almacenSesionProvider.overrideWithValue(almacen),
        obtenerCatalogoUseCaseProvider.overrideWithValue(
          ObtenerCatalogoUseCase(_CatalogoFalso()),
        ),
        intervalosSondeoProvider.overrideWithValue(
          const [Duration(milliseconds: 50)],
        ),
      ],
      child: const MaterialApp(
        home: PagoScreen(idServicio: 1, idProducto: 2),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (repo: repo, almacen: almacen);
}

Future<void> _hastaDialogoPin(WidgetTester tester) async {
  final campos = find.byType(TextField);
  await tester.enterText(campos.at(0), '5512345678');
  await tester.enterText(campos.at(1), '5512345678');
  await tester.tap(find.text('Revisar pago'));
  await tester.pumpAndSettle();
  // El botón dice lo que hace: "Pagar" con el importe.
  await tester.tap(find.text('Pagar \$100.00'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('captura de teléfono: no coinciden muestra el texto exacto', (tester) async {
    await _montar(tester, RepoPagoFalso());
    final campos = find.byType(TextField);
    await tester.enterText(campos.at(0), '5512345678');
    await tester.enterText(campos.at(1), '5512345670');
    await tester.tap(find.text('Revisar pago'));
    await tester.pumpAndSettle();
    expect(find.text('Los números no coinciden.'), findsOneWidget);
  });

  testWidgets('el resumen muestra la cuenta enmascarada, nunca el número completo', (tester) async {
    await _montar(tester, RepoPagoFalso());
    final campos = find.byType(TextField);
    await tester.enterText(campos.at(0), '5512345678');
    await tester.enterText(campos.at(1), '5512345678');
    await tester.tap(find.text('Revisar pago'));
    await tester.pumpAndSettle();
    expect(find.text('********1188'), findsOneWidget);
    expect(find.textContaining('695429041188'), findsNothing);
    expect(find.text('Detalle del pago'), findsOneWidget);
  });

  testWidgets('saldo insuficiente deshabilita Pagar y muestra el aviso', (tester) async {
    await _montar(tester, RepoPagoFalso(cuentas: const [cuentaPobre]));
    final campos = find.byType(TextField);
    await tester.enterText(campos.at(0), '5512345678');
    await tester.enterText(campos.at(1), '5512345678');
    await tester.tap(find.text('Revisar pago'));
    await tester.pumpAndSettle();
    final boton = tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Pagar \$100.00'));
    expect(boton.onPressed, isNull);
    expect(find.textContaining('Saldo insuficiente'), findsOneWidget);
  });

  testWidgets('el diálogo del PIN es oculto, numérico y de 4 dígitos', (tester) async {
    await _montar(tester, RepoPagoFalso());
    await _hastaDialogoPin(tester);
    final campo = tester.widget<TextField>(find.descendant(
      of: find.byType(DialogoPin),
      matching: find.byType(TextField),
    ));
    expect(campo.obscureText, isTrue);
    expect(campo.keyboardType, TextInputType.number);
    expect(campo.maxLength, 4);
    expect(campo.autocorrect, isFalse);
    expect(campo.enableSuggestions, isFalse);

    final confirmar = find.widgetWithText(FilledButton, 'Confirmar pago');
    expect(tester.widget<FilledButton>(confirmar).onPressed, isNull);
    await tester.enterText(find.byType(TextField).last, '12ab34');
    await tester.pump();
    expect(campo.controller!.text, '1234');
    expect(tester.widget<FilledButton>(confirmar).onPressed, isNotNull);
  });

  testWidgets('pago exitoso muestra los textos exactos y no toca el almacenamiento', (tester) async {
    final repo = RepoPagoFalso()
      ..respuestasPago = [ok200(comprobante(EstatusPago.confirmada, folio: 'F123'))];
    final entorno = await _montar(tester, repo);
    await _hastaDialogoPin(tester);
    await tester.enterText(find.byType(TextField).last, '1234');
    await tester.pump();
    await tester.tap(find.widgetWithText(FilledButton, 'Confirmar pago'));
    await tester.pumpAndSettle();

    expect(find.text('¡Pago realizado!'), findsOneWidget);
    expect(
      find.text('Tu pago de Telcel por \$100.00 se aplicó correctamente. Folio: F123.'),
      findsOneWidget,
    );
    expect(find.text('Ver comprobante'), findsOneWidget);
    expect(repo.pagos, hasLength(1));
    expect(entorno.almacen.accesos, 0);
  });

  testWidgets('PIN incorrecto muestra el aviso con los intentos', (tester) async {
    final repo = RepoPagoFalso()
      ..respuestasPago = [fallo(403, motivo: MotivoPago.pinIncorrecto, intentos: 2)];
    await _montar(tester, repo);
    await _hastaDialogoPin(tester);
    await tester.enterText(find.byType(TextField).last, '0000');
    await tester.pump();
    await tester.tap(find.widgetWithText(FilledButton, 'Confirmar pago'));
    await tester.pumpAndSettle();
    expect(find.text('PIN incorrecto. Te quedan 2 intentos.'), findsOneWidget);
    // El campo del nuevo intento empieza vacío.
    final campo = tester.widget<TextField>(find.descendant(
      of: find.byType(DialogoPin),
      matching: find.byType(TextField),
    ));
    expect(campo.controller!.text, isEmpty);
  });

  testWidgets('sin respuesta del servidor muestra Estamos confirmando (ámbar) con indicador', (tester) async {
    final repo = RepoPagoFalso()
      ..respuestasPago = [ok202(comprobante(EstatusPago.indeterminada))]
      ..respuestasEstado = [ok200(comprobante(EstatusPago.pendiente))];
    await _montar(tester, repo);
    await _hastaDialogoPin(tester);
    await tester.enterText(find.byType(TextField).last, '1234');
    await tester.pump();
    await tester.tap(find.widgetWithText(FilledButton, 'Confirmar pago'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 10));
    expect(find.text('Estamos confirmando tu pago'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.textContaining('Número de operación: K3J9X0A7ZQ.'), findsOneWidget);
    expect(find.text('¡Pago realizado!'), findsNothing);
    await tester.pumpAndSettle(const Duration(milliseconds: 100));
  });

  testWidgets('rechazo 422 muestra el texto amigable y nunca el del servidor', (tester) async {
    final repo = RepoPagoFalso()
      ..respuestasPago = [
        fallo(422, comprobante: comprobante(EstatusPago.rechazada, motivo: MotivoPago.rechazadoProveedor)),
      ];
    await _montar(tester, repo);
    await _hastaDialogoPin(tester);
    await tester.enterText(find.byType(TextField).last, '1234');
    await tester.pump();
    await tester.tap(find.widgetWithText(FilledButton, 'Confirmar pago'));
    await tester.pumpAndSettle();
    expect(find.text('No se realizó el pago'), findsOneWidget);
    expect(find.text('La operación fue rechazada.'), findsOneWidget);
    expect(find.text('No se hizo ningún cargo a tu cuenta.'), findsOneWidget);
    expect(find.text('Intentar de nuevo'), findsOneWidget);
  });

  testWidgets('comprobante: código de tarjeta con Copiar y leyenda solo si es exitoso', (tester) async {
    String? copiado;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copiado = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(() => tester.binding.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null));

    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: ComprobanteVista(
          comprobante: comprobante(
            EstatusPago.confirmada,
            folio: 'F1',
            pinServicio: 'ABCD-1234',
            leyenda: '<b>Gracias</b>',
          ),
        ),
      ),
    ));
    expect(find.text('Código de tu tarjeta'), findsOneWidget);
    expect(find.text('ABCD-1234'), findsOneWidget);
    expect(find.text('<b>Gracias</b>'), findsOneWidget);
    expect(find.text('Folio'), findsOneWidget);
    expect(find.text('Exitoso'), findsOneWidget);
    // Dos datos copiables (folio y código de la tarjeta): se copia el código.
    expect(find.text('Copiar'), findsNWidgets(2));
    await tester.ensureVisible(find.text('Copiar').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Copiar').last);
    await tester.pump();
    expect(copiado, 'ABCD-1234');

    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: ComprobanteVista(
          comprobante: comprobante(
            EstatusPago.indeterminada,
            pinServicio: 'ABCD-1234',
            leyenda: 'texto',
          ),
        ),
      ),
    ));
    expect(find.text('Código de tu tarjeta'), findsNothing);
    expect(find.text('texto'), findsNothing);
    expect(find.text('Número de operación'), findsOneWidget);
    expect(find.text('Pendiente'), findsOneWidget);
  });
}
