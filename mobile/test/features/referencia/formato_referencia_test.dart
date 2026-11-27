import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/utils/formato_referencia.dart';

void main() {
  test('vacia o solo espacios es vacia', () {
    expect(validarFormatoReferencia(''), ErrorFormatoReferencia.vacia);
    expect(validarFormatoReferencia('   '), ErrorFormatoReferencia.vacia);
  });

  test('espacios al inicio y al final se recortan y son validos', () {
    expect(recortarReferencia('  0012345678  '), '0012345678');
    expect(validarFormatoReferencia('  0012345678  '), isNull);
  });

  test('espacios en medio son invalidos', () {
    expect(validarFormatoReferencia('00123 456'), ErrorFormatoReferencia.invalida);
  });

  test('simbolos y acentos son invalidos', () {
    expect(validarFormatoReferencia('abc-123'), ErrorFormatoReferencia.invalida);
    expect(validarFormatoReferencia('abc_123'), ErrorFormatoReferencia.invalida);
    expect(validarFormatoReferencia('ñandú1'), ErrorFormatoReferencia.invalida);
  });

  test('letras y numeros son validos', () {
    expect(validarFormatoReferencia('AbC123'), isNull);
  });

  test('100 caracteres es valido y 101 no', () {
    expect(validarFormatoReferencia('A' * 100), isNull);
    expect(validarFormatoReferencia('A' * 101), ErrorFormatoReferencia.invalida);
  });
}
