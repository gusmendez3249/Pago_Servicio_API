import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';
import 'app_scaffold.dart';
import 'estado_operacion.dart';
import 'logo_bangus.dart';
import 'pagina_flujo.dart';

/// App mínima que se muestra cuando falta `API_BASE_URL`.
class AppErrorConfiguracion extends StatelessWidget {
  const AppErrorConfiguracion({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'BanGus',
      theme: construirTema(),
      debugShowCheckedModeBanner: false,
      home: const PantallaErrorConfiguracion(),
    );
  }
}

class PantallaErrorConfiguracion extends StatelessWidget {
  const PantallaErrorConfiguracion({super.key});

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return AppScaffold(
      cuerpo: PaginaFlujo(
        cabecera: Builder(
          // Colores de la franja (sobre negro), no los del contexto claro.
          builder: (context) {
            final colores = ColoresApp.de(context);
            final tema = Theme.of(context);
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const LogotipoBanGus(tamanoTexto: Escala.s28),
                const SizedBox(height: Esp.x6),
                const IconoEstado(tipo: TipoEstado.error),
                const SizedBox(height: Esp.x4),
                Text(
                  'BanGus no está disponible',
                  style: tema.textTheme.headlineMedium?.copyWith(
                    color: colores.texto,
                  ),
                ),
              ],
            );
          },
        ),
        children: [
          Text(
            'Por ahora no podemos atenderte. Inténtalo de nuevo más tarde.',
            style: tema.textTheme.bodyLarge,
          ),
          // El detalle técnico solo se muestra a quien desarrolla (modo debug).
          if (kDebugMode) ...[
            const SizedBox(height: Esp.x6),
            const Text(
                'Falta la variable API_BASE_URL al compilar o ejecutar:'),
            const SizedBox(height: Esp.x2),
            const SelectableText(
              'flutter run --dart-define=API_BASE_URL=<url-del-backend>',
            ),
          ],
        ],
      ),
    );
  }
}
