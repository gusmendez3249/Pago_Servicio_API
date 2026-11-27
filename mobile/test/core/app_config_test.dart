import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/config/app_config.dart';

void main() {
  group('AppConfig.urlValida', () {
    test('en release exige https salvo hosts locales', () {
      expect(AppConfig.urlValida('https://api.bangus.mx', release: true), isTrue);
      expect(AppConfig.urlValida('http://api.bangus.mx', release: true), isFalse);
      expect(AppConfig.urlValida('http://localhost:9095', release: true), isTrue);
      expect(AppConfig.urlValida('http://10.0.2.2:9095', release: true), isTrue);
    });

    test('en desarrollo acepta http y rechaza esquemas o URL inválidas', () {
      expect(AppConfig.urlValida('http://192.168.1.20:9095', release: false), isTrue);
      expect(AppConfig.urlValida('ftp://api.bangus.mx', release: false), isFalse);
      expect(AppConfig.urlValida('', release: false), isFalse);
      expect(AppConfig.urlValida('api.bangus.mx', release: false), isFalse);
    });
  });
}
