import 'dart:async';

import 'package:gesto_pago_mobile/core/config/app_theme.dart';
import 'package:google_fonts/google_fonts.dart';

/// Configuración global de pruebas: sin descargas de fuentes.
Future<void> testExecutable(FutureOr<void> Function() testMain) async {
  GoogleFonts.config.allowRuntimeFetching = false;
  AppFuentes.habilitadas = false;
  await testMain();
}
