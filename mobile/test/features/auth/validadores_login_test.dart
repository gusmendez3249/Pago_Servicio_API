import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/features/auth/presentation/widgets/validadores_login.dart';

void main() {
  group('usuario', () {
    test('obligatorio', () {
      expect(ValidadoresLogin.usuario(null), isNotNull);
      expect(ValidadoresLogin.usuario(''), isNotNull);
    });
    test('longitud 3 a 50', () {
      expect(ValidadoresLogin.usuario('ab'), isNotNull);
      expect(ValidadoresLogin.usuario('abc'), isNull);
      expect(ValidadoresLogin.usuario('a' * 50), isNull);
      expect(ValidadoresLogin.usuario('a' * 51), isNotNull);
    });
    test('caracteres permitidos', () {
      expect(ValidadoresLogin.usuario('juan.perez-1_x'), isNull);
      expect(ValidadoresLogin.usuario('juan perez'), isNotNull);
      expect(ValidadoresLogin.usuario('juan@x'), isNotNull);
    });
  });

  group('password', () {
    test('obligatoria', () {
      expect(ValidadoresLogin.password(null), isNotNull);
      expect(ValidadoresLogin.password(''), isNotNull);
    });
    test('longitud 8 a 72', () {
      expect(ValidadoresLogin.password('1234567'), isNotNull);
      expect(ValidadoresLogin.password('12345678'), isNull);
      expect(ValidadoresLogin.password('a' * 72), isNull);
      expect(ValidadoresLogin.password('a' * 73), isNotNull);
    });
  });
}
