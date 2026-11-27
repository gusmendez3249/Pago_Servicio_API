import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/shared/entrada/entrada.dart';

TextEditingValue _escribir(
        TextInputFormatter f, String anterior, String nuevo) =>
    f.formatEditUpdate(
      TextEditingValue(
          text: anterior,
          selection: TextSelection.collapsed(offset: anterior.length)),
      TextEditingValue(
          text: nuevo,
          selection: TextSelection.collapsed(offset: nuevo.length)),
    );

void main() {
  group('limpiarEntrada (mismas reglas que la web)', () {
    test('dígitos, nombre, CURP y RFC', () {
      expect(limpiarEntrada(Entrada.digitos, '41a8-12 34567890', maximo: 10),
          '4181234567');
      expect(limpiarEntrada(Entrada.nombre, '  José  María<1>'), 'José María');
      expect(limpiarEntrada(Entrada.curp, 'garl-950312mgtjpd01xx'),
          'GARL950312MGTJPD01');
      expect(limpiarEntrada(Entrada.rfc, 'ñañ&950312 qa1'), 'ÑAÑ&950312QA1');
    });

    test('correo, usuario y contraseña', () {
      expect(
          limpiarEntrada(Entrada.correo, 'ana @mail@x.com'), 'ana@mailx.com');
      expect(limpiarEntrada(Entrada.usuario, 'ana garcía!'), 'anagarca');
      expect(limpiarEntrada(Entrada.password, 'Ab c#1\u0000\t'), 'Abc#1');
    });

    test('importe con 2 decimales y enteros limitados', () {
      expect(limpiarEntrada(Entrada.importe, '0012a.3.456'), '12.34');
      expect(limpiarEntrada(Entrada.importe, '1234567', enteros: 5), '12345');
    });

    test('referencia, búsqueda y domicilio', () {
      expect(limpiarEntrada(Entrada.alfanumerico, 'AB-12 cd'), 'AB12cd');
      expect(limpiarEntrada(Entrada.busqueda, '<img src=x>CFE'), 'img srcxCFE');
      expect(limpiarEntrada(Entrada.domicilio, 'Av. Hidalgo #12 <b>'),
          'Av. Hidalgo #12 b');
    });
  });

  group('FormatoEntrada', () {
    test('pegar una CURP con guiones la deja limpia y con el cursor al final',
        () {
      final r =
          _escribir(FormatoEntrada(Entrada.curp), '', 'garl-950312-mgtjpd01-x');
      expect(r.text, 'GARL950312MGTJPD01');
      expect(r.selection.extentOffset, 18);
    });

    test('un importe por encima del tope conserva el valor anterior', () {
      final f = FormatoEntrada(Entrada.importe, enteros: 5, valorMaximo: 50000);
      expect(_escribir(f, '5000', '50000').text, '50000');
      expect(_escribir(f, '50000', '500009').text, '50000');
      expect(_escribir(f, '500', '500.999').text, '500.99');
    });
  });
}
