import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import 'app.dart';
import 'core/config/app_config.dart';
import 'core/session/session_controller.dart';
import 'shared/widgets/pantalla_error_configuracion.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Las fuentes vienen en assets/fonts: la app nunca contacta a Google (privacidad y uso sin conexión)
  GoogleFonts.config.allowRuntimeFetching = false;

  if (!AppConfig.configuracionValida) {
    runApp(const AppErrorConfiguracion());
    return;
  }

  final contenedor = ProviderContainer();
  await contenedor.read(sessionControllerProvider.notifier).restaurar();

  runApp(
    UncontrolledProviderScope(
      container: contenedor,
      child: const GestoPagoApp(),
    ),
  );
}
