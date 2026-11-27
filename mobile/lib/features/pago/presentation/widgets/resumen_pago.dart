import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../shared/widgets/banner_mensaje.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../../../../shared/widgets/pagina_flujo.dart';
import '../../../catalogo/domain/utils/formato_catalogo.dart';
import '../../../catalogo/presentation/widgets/cabecera_servicio.dart';
import '../providers/pago_state.dart';
import '../textos_pago.dart';

/// Resumen previo a la confirmación, igual que la web: "Revisa y confirma"
/// con el importe a pagar en la franja; tarjeta "Detalle del pago" con la
/// cuenta de cargo; "Pagar $X", una acción secundaria y la nota del PIN.
/// Muestra solo la cuenta enmascarada.
class ResumenPagoVista extends StatelessWidget {
  const ResumenPagoVista({
    super.key,
    required this.estado,
    required this.alPagar,
    required this.alCambiarDatos,
    required this.alVolver,
    required this.alSeleccionarCuenta,
  });

  final PagoState estado;
  final VoidCallback alPagar;
  final VoidCallback alCambiarDatos;
  final VoidCallback alVolver;
  final ValueChanged<int> alSeleccionarCuenta;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final resumen = estado.resumen;
    final producto = estado.producto;
    final cuenta = estado.cuenta;
    if (resumen == null || producto == null || cuenta == null) {
      return const SizedBox.shrink();
    }
    final saldoSuficiente = estado.saldoSuficiente;
    final ocupado = estado.fase != FasePago.resumen;
    final total = formatearMoneda(resumen.total);

    return PaginaFlujo(
      paso: 3,
      segundoPaso: estado.esReferencia ? 'Referencia' : 'Teléfono',
      cancelable: !ocupado,
      cabecera: CabeceraServicio(
        logoUrl: producto.logoUrl,
        nombreLogo: producto.servicio,
        titulo: TextosPago.revisaYConfirma,
        subtitulo: [
          producto.servicio,
          if (producto.producto.isNotEmpty) producto.producto,
        ].join(', '),
        etiquetaDato: TextosPago.importeAPagar,
        dato: total,
      ),
      pie: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          FilledButton(
            onPressed: (saldoSuficiente && !ocupado) ? alPagar : null,
            child: Text(TextosPago.pagarImporte(resumen.total)),
          ),
          const SizedBox(height: Esp.x3),
          OutlinedButton(
            onPressed:
                ocupado ? null : (saldoSuficiente ? alCambiarDatos : alVolver),
            child: Text(
              saldoSuficiente ? TextosPago.cambiarDatos : TextosPago.volver,
            ),
          ),
          if (saldoSuficiente) ...[
            const SizedBox(height: Esp.x2),
            Text(TextosPago.notaPin, style: tema.textTheme.bodySmall),
          ],
        ],
      ),
      children: [
        TarjetaDatos(
          relleno: const EdgeInsets.fromLTRB(Esp.x6, Esp.x6, Esp.x6, Esp.x3),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Semantics(
                header: true,
                child: Text(
                  TextosPago.detallePago,
                  style: tema.textTheme.titleMedium,
                ),
              ),
              const SizedBox(height: Esp.x3),
              FichaDatos(
                filas: [
                  FilaDato(TextosPago.servicio, producto.servicio),
                  FilaDato(
                    TextosPago.producto,
                    producto.producto.isEmpty ? '—' : producto.producto,
                  ),
                  FilaDato(
                    estado.esReferencia
                        ? TextosPago.referencia
                        : TextosPago.telefono,
                    estado.esReferencia ? estado.referencia : estado.telefono,
                  ),
                  FilaDato(TextosPago.importeResumen, total),
                  FilaDato(TextosPago.cuentaCargo, cuenta.numCuentaEnmascarada),
                  FilaDato(
                    TextosPago.saldoDisponible,
                    formatearMoneda(resumen.saldo),
                  ),
                  FilaDato(
                    TextosPago.saldoDespues,
                    saldoSuficiente
                        ? formatearMoneda(resumen.saldoDespues)
                        : '—',
                  ),
                ],
              ),
              if (estado.cuentas.length > 1) ...[
                const SizedBox(height: Esp.x3),
                DropdownButtonFormField<int>(
                  initialValue: estado.cuentaIndex,
                  decoration: const InputDecoration(
                    labelText: TextosPago.cuentaCargo,
                  ),
                  dropdownColor: ColoresApp.de(context).superficie,
                  items: [
                    for (var i = 0; i < estado.cuentas.length; i++)
                      DropdownMenuItem(
                        value: i,
                        child: Text(
                          estado.cuentas[i].numCuentaEnmascarada,
                          style: estiloCampoCifras().copyWith(
                            fontSize: Escala.s16,
                          ),
                        ),
                      ),
                  ],
                  onChanged: ocupado
                      ? null
                      : (i) {
                          if (i != null) alSeleccionarCuenta(i);
                        },
                ),
              ],
              if (!saldoSuficiente) ...[
                const SizedBox(height: Esp.x4),
                BannerMensaje(
                  tipo: TipoBanner.error,
                  titulo: TextosPago.saldoInsuficienteTitulo,
                  mensaje: TextosPago.saldoInsuficienteMensaje(
                    resumen.saldo,
                    resumen.total,
                  ),
                ),
              ],
              const SizedBox(height: Esp.x3),
            ],
          ),
        ),
      ],
    );
  }
}
