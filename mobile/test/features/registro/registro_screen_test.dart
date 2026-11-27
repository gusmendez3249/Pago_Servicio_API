import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/features/registro/data/registro_remote_datasource.dart';
import 'package:gesto_pago_mobile/features/registro/presentation/registro_screen.dart';

class _RegistroFalso implements RegistroRemoteDatasource {
  Map<String, Object?>? enviada;
  Result<CuentaCreada> respuesta = const Ok(
    CuentaCreada(
      nombreCompleto: 'Laura Garcia Lopez',
      username: 'laura.garcia',
      numeroCuenta: '846570860170',
      saldoInicial: 0,
    ),
    httpStatus: 201,
  );

  @override
  Future<Result<List<String>>> nacionalidades() async =>
      const Ok(['MEXICANA', 'CANADIENSE']);

  @override
  Future<Result<CuentaCreada>> registrar(Map<String, Object?> solicitud) async {
    enviada = solicitud;
    return respuesta;
  }
}

Future<_RegistroFalso> _montar(WidgetTester tester) async {
  tester.view.physicalSize = const Size(800, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final falso = _RegistroFalso();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [registroDatasourceProvider.overrideWithValue(falso)],
      child: MaterialApp(home: RegistroScreen(hoy: DateTime(2026, 11, 27))),
    ),
  );
  await tester.pumpAndSettle();
  return falso;
}

Future<void> _escribir(WidgetTester tester, String campo, String texto) async {
  final f = find.byKey(ValueKey('registro-$campo'));
  await tester.ensureVisible(f);
  await tester.enterText(f, texto);
}

Future<void> _elegir(WidgetTester tester, String campo, String opcion) async {
  final f = find.byKey(ValueKey('registro-$campo'));
  await tester.ensureVisible(f);
  await tester.tap(f);
  await tester.pumpAndSettle();
  await tester.tap(find.text(opcion).last);
  await tester.pumpAndSettle();
}

Future<void> _continuar(WidgetTester tester, [String boton = 'Continuar']) async {
  final f = find.widgetWithText(FilledButton, boton);
  await tester.ensureVisible(f);
  await tester.tap(f);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('no avanza con datos vacíos y lo dice en cada campo', (tester) async {
    await _montar(tester);

    await _continuar(tester);

    expect(find.text('Paso 1 de 4  '), findsNothing);
    expect(find.text('Este dato es obligatorio.'), findsNWidgets(3));
    expect(find.text('Indica tu fecha de nacimiento.'), findsOneWidget);
    expect(find.text('Escribe tu CURP.'), findsOneWidget);
    expect(find.textContaining('Tus datos'), findsOneWidget);
  });

  testWidgets('registro completo en 4 pasos: la cuenta nace en \$0.00',
      (tester) async {
    final falso = await _montar(tester);

    await _escribir(tester, 'nombre', 'Laura');
    await _escribir(tester, 'apellidoPaterno', 'Garcia');
    await _escribir(tester, 'apellidoMaterno', 'Lopez');
    // Fecha: el selector en modo texto
    await tester.ensureVisible(find.byKey(const ValueKey('registro-fechaNacimiento')));
    await tester.tap(find.byKey(const ValueKey('registro-fechaNacimiento')));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.edit_outlined));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, '03/12/1995');
    await tester.tap(find.text('Aceptar'));
    await tester.pumpAndSettle();
    await _elegir(tester, 'sexo', 'Femenino');
    await _elegir(tester, 'estadoCivil', 'Soltero(a)');
    await _escribir(tester, 'curp', 'garl950312mgtjpd01');
    await _escribir(tester, 'rfc', 'GARL950312QA1');
    await _continuar(tester);

    expect(find.textContaining('Contacto y domicilio'), findsOneWidget);
    await _escribir(tester, 'correo', 'laura@example.com');
    await _escribir(tester, 'telefono', '4181239876');
    await _escribir(tester, 'calle', 'Hidalgo');
    await _escribir(tester, 'numeroExterior', '12');
    await _escribir(tester, 'colonia', 'Centro');
    await _escribir(tester, 'codigoPostal', '37800');
    await _escribir(tester, 'municipio', 'Dolores Hidalgo');
    await _escribir(tester, 'estado', 'Guanajuato');
    await _continuar(tester);

    await _escribir(tester, 'ocupacion', 'Contadora');
    await _escribir(tester, 'empresa', 'Despacho');
    await _escribir(tester, 'ingresoMensual', '18500');
    await _continuar(tester);

    await _escribir(tester, 'username', 'laura.garcia');
    await _escribir(tester, 'password', 'Prueba#Ficticia9');
    await _escribir(tester, 'confirmacion', 'Prueba#Ficticia9');
    await _escribir(tester, 'pin', '1234');
    await _escribir(tester, 'confirmacionPin', '1234');
    await _continuar(tester, 'Crear cuenta');
    expect(find.text('Evita PIN como 1111 o 1234.'), findsOneWidget);
    expect(falso.enviada, isNull);

    await _escribir(tester, 'pin', '4826');
    await _escribir(tester, 'confirmacionPin', '4826');
    await _continuar(tester, 'Crear cuenta');

    final personales = falso.enviada!['datosPersonales']! as Map;
    expect(personales['curp'], 'GARL950312MGTJPD01');
    expect(personales['fechaNacimiento'], '1995-03-12');
    expect(personales['sexo'], 'FEMENINO');
    expect((falso.enviada!['datosContacto']! as Map)['telefonoMovil'], 4181239876);
    expect(find.text('Tu cuenta está lista'), findsOneWidget);
    expect(find.text('846570860170'), findsOneWidget);
    expect(find.textContaining('\$0.00'), findsWidgets);
  });

  testWidgets('sin completar el formulario no envía nada ni muestra texto del servidor', (tester) async {
    final falso = await _montar(tester);
    falso.respuesta = const Failure(
      tipo: TipoFallo.desconocido,
      mensaje: 'texto técnico del servidor',
      httpStatus: 409,
    );
    // Sin completar el formulario no se envía nada
    await _continuar(tester);
    expect(falso.enviada, isNull);
    expect(find.text('texto técnico del servidor'), findsNothing);
  });
}
