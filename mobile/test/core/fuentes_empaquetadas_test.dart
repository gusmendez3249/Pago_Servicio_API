import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Cada variante que usa el tema (Libre Franklin 400-700 y Source Serif 4 600)
/// debe venir empaquetada: con allowRuntimeFetching en false no hay descarga.
void main() {
  test('las fuentes del tema están empaquetadas y declaradas', () {
    const archivos = [
      'LibreFranklin-Regular.ttf',
      'LibreFranklin-Medium.ttf',
      'LibreFranklin-SemiBold.ttf',
      'LibreFranklin-Bold.ttf',
      'SourceSerif4-SemiBold.ttf',
    ];
    for (final nombre in archivos) {
      final archivo = File('assets/fonts/$nombre');
      expect(archivo.existsSync(), isTrue, reason: nombre);
      expect(archivo.lengthSync(), greaterThan(10000), reason: nombre);
    }
    expect(File('pubspec.yaml').readAsStringSync(), contains('- assets/fonts/'));
    expect(File('lib/main.dart').readAsStringSync(),
        contains('GoogleFonts.config.allowRuntimeFetching = false'));
  });
}
