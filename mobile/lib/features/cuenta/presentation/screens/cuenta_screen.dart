import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../core/http/result.dart';
import '../../../../core/session/session_controller.dart';
import '../../../../shared/widgets/app_scaffold.dart';
import '../../../../shared/widgets/barra_secciones.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../../../auth/presentation/widgets/boton_cerrar_sesion.dart';
import '../../../catalogo/domain/utils/formato_catalogo.dart';
import '../../../catalogo/presentation/widgets/estados_catalogo.dart';
import '../../../pago/domain/entities/cuenta_cliente.dart';
import '../providers/cuenta_providers.dart';

/// Nombre en formato de título ("ANA MARÍA LÓPEZ" → "Ana María López").
String nombreLegible(String? nombre) {
  final partes = (nombre ?? '')
      .trim()
      .split(RegExp(r'\s+'))
      .where((p) => p.isNotEmpty)
      .map((p) => p[0].toUpperCase() + p.substring(1).toLowerCase());
  return partes.join(' ');
}

/// Pantalla "Cuenta", igual que la web: "Tu cuenta" con el nombre en la
/// franja; "Cuentas de cargo" en renglones y la sección "Sesión".
class CuentaScreen extends ConsumerWidget {
  const CuentaScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tema = Theme.of(context);
    final sesion = ref.watch(sessionControllerProvider);
    final nombre = nombreLegible(sesion.nombreCliente);
    final cuentas = ref.watch(cuentasClienteProvider);
    void reintentar() => ref.invalidate(cuentasClienteProvider);

    final Widget listaCuentas = cuentas.when(
      loading: () => const TarjetaDatos(
        relleno: EdgeInsets.zero,
        child: Column(
          children: [
            EsqueletoTarjeta(conLogo: false),
            Divider(),
            EsqueletoTarjeta(conLogo: false),
          ],
        ),
      ),
      error: (_, __) => _ErrorCuentas(alReintentar: reintentar),
      data: (resultado) => switch (resultado) {
        Ok(:final data) => data.isEmpty
            ? const MensajeEstado(
                margen: 0,
                vacio: true,
                icono: Icons.account_balance_wallet_outlined,
                titulo: 'Aún no tienes cuentas activas para pagar.',
              )
            : TarjetaDatos(
                relleno: EdgeInsets.zero,
                child: Column(
                  children: [
                    for (var i = 0; i < data.length; i++) ...[
                      if (i > 0) const Divider(),
                      _FilaCuenta(cuenta: data[i]),
                    ],
                  ],
                ),
              ),
        Failure() => _ErrorCuentas(alReintentar: reintentar),
      },
    );

    return AppScaffold(
      seccion: SeccionApp.cuenta,
      cuerpo: MarcoBanda(
        marca: true,
        cabecera: Builder(
          // Colores de la franja (sobre negro), no los del contexto claro.
          builder: (context) {
            final colores = ColoresApp.de(context);
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Semantics(
                  header: true,
                  child: Text(
                    'Tu cuenta',
                    style: estiloTitular(
                      tamano: Escala.s28,
                      color: colores.texto,
                    ),
                  ),
                ),
                if (nombre.isNotEmpty) ...[
                  const SizedBox(height: Esp.x1),
                  Text(
                    nombre,
                    style: estiloTexto(
                      tamano: Escala.s14,
                      color: colores.textoSecundario,
                    ),
                  ),
                ],
              ],
            );
          },
        ),
        cuerpo: ListView(
          padding: const EdgeInsets.fromLTRB(
            Esp.margen,
            Esp.x6,
            Esp.margen,
            Esp.x8,
          ),
          children: [
            Text('Cuentas de cargo', style: tema.textTheme.titleMedium),
            const SizedBox(height: Esp.x3),
            listaCuentas,
            const SizedBox(height: Esp.x8),
            Text('Sesión', style: tema.textTheme.titleMedium),
            const SizedBox(height: Esp.x3),
            const BotonCerrarSesion(),
          ],
        ),
      ),
    );
  }
}

/// Renglón de una cuenta: número enmascarado, "Saldo disponible" y saldo.
class _FilaCuenta extends StatelessWidget {
  const _FilaCuenta({required this.cuenta});

  final CuentaCliente cuenta;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 72),
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: Esp.x4,
          vertical: Esp.x3,
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    'Cuenta ${cuenta.numCuentaEnmascarada}',
                    style: tema.textTheme.titleMedium?.copyWith(
                      fontFeatures: kCifras,
                    ),
                  ),
                  Text('Saldo disponible', style: tema.textTheme.bodySmall),
                ],
              ),
            ),
            const SizedBox(width: Esp.x4),
            Text(
              formatearMoneda(cuenta.saldo),
              style: tema.textTheme.titleMedium?.copyWith(
                fontFeatures: kCifras,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorCuentas extends StatelessWidget {
  const _ErrorCuentas({required this.alReintentar});

  final VoidCallback alReintentar;

  @override
  Widget build(BuildContext context) {
    return MensajeEstado(
      margen: 0,
      icono: Icons.error_outline_rounded,
      titulo: 'No pudimos cargar tus cuentas. Inténtalo de nuevo.',
      etiquetaAccion: 'Reintentar',
      alAccionar: alReintentar,
    );
  }
}
