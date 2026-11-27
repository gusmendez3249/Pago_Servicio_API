import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/features/registro/domain/reglas_registro.dart';

void main() {
  test('dígito verificador de la CURP', () {
    expect(digitoCurpValido('PERJ950515HDFRMN06'), isTrue);
    expect(digitoCurpValido('PERJ950515HDFRMN01'), isFalse);
  });

  test('la CURP debe coincidir con fecha y sexo', () {
    final f = DateTime(1995, 5, 15);
    expect(errorCurp('PERJ950515HDFRMN06', f, 'MASCULINO'), isNull);
    expect(errorCurp('PERJ950515HDFRMN06', DateTime(1985, 5, 15), 'MASCULINO'),
        ErrorCurp.fecha);
    expect(errorCurp('PERJ950515HDFRMN06', f, 'FEMENINO'), ErrorCurp.sexo);
    expect(errorCurp('perj', f, 'MASCULINO'), ErrorCurp.formato);
  });

  test('el RFC corresponde a la CURP y no es genérico', () {
    expect(errorRfc('PERJ9505151A2', 'PERJ950515HDFRMN06'), isNull);
    expect(errorRfc('XAXX950515AB1', 'PERJ950515HDFRMN06'), ErrorRfc.generico);
    expect(errorRfc('LOPJ8505151A2', 'PERJ950515HDFRMN06'), ErrorRfc.curp);
  });

  test('edad, contraseña, PIN y nombre', () {
    final hoy = DateTime(2026, 11, 27);
    expect(edad(DateTime(2008, 11, 27), hoy), 18);
    expect(edad(DateTime(2008, 11, 28), hoy), 17);
    expect(passwordValida('Password123!'), isTrue);
    expect(passwordValida('password123'), isFalse);
    expect(passwordValida('Pass word1!'), isFalse);
    expect(pinTrivial('1111'), isTrue);
    expect(pinTrivial('4321'), isTrue);
    expect(pinTrivial('4826'), isFalse);
    expect(nombreValido('Ana'), isTrue);
    expect(nombreValido('Ana  María'), isFalse);
  });
}
