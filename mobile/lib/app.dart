import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/config/app_theme.dart';
import 'core/router/app_router.dart';
import 'core/session/session_controller.dart';

class GestoPagoApp extends ConsumerStatefulWidget {
  const GestoPagoApp({super.key});

  @override
  ConsumerState<GestoPagoApp> createState() => _GestoPagoAppState();
}

class _GestoPagoAppState extends ConsumerState<GestoPagoApp>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      ref.read(sessionControllerProvider.notifier).alVolverDeSegundoPlano();
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'BanGus',
      debugShowCheckedModeBanner: false,
      theme: construirTema(),
      routerConfig: ref.watch(routerProvider),
    );
  }
}
