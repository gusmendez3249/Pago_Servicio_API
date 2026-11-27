import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../shared/widgets/acceso_circular.dart';
import '../../../../shared/widgets/banner_mensaje.dart';
import '../../../../shared/widgets/estado_operacion.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../../../../shared/widgets/logo_bangus.dart';
import '../../../../shared/widgets/pagina_flujo.dart';
import '../../../catalogo/domain/utils/formato_catalogo.dart';
import '../../../catalogo/presentation/widgets/logo_servicio.dart';
import '../../../historial/domain/utils/reglas_historial.dart';
import '../../domain/entities/comprobante_pago.dart';
import '../textos_pago.dart';
import 'resultado_vista.dart';

/// Tipo de estado visual de un estatus de pago.
TipoEstado tipoEstadoDe(EstatusPago e) => switch (e) {
      EstatusPago.confirmada => TipoEstado.exito,
      EstatusPago.rechazada => TipoEstado.error,
      _ => TipoEstado.pendiente,
    };

/// Título del estado en la franja del comprobante (mismos de la web).
String tituloEstado(EstatusPago e) => switch (e) {
      EstatusPago.confirmada => TextosPago.estadoExitoso,
      EstatusPago.rechazada => TextosPago.estadoRechazado,
      _ => TextosPago.estadoPendiente,
    };

/// Comprobante de la operación, igual que la web: estado y total en la
/// franja; tarjeta "Comprobante" con el logotipo de BanGus, la ficha de datos,
/// el folio y el código copiables, el motivo y la leyenda; debajo, el aviso
/// opcional y las acciones como accesos circulares.
/// `pinServicio` y `leyenda` solo se muestran cuando el estatus es
/// CONFIRMADA; la leyenda siempre como texto plano.
class ComprobanteVista extends StatelessWidget {
  const ComprobanteVista({
    super.key,
    required this.comprobante,
    this.titulo,
    this.servicioPorDefecto,
    this.productoPorDefecto,
    this.totalPorDefecto,
    this.mostrarLogo = false,
    this.mostrarMotivo = false,
    this.aviso,
    this.acciones = const <AccionResultado>[],
  });

  final ComprobantePago comprobante;

  /// Texto junto a la flecha de regreso (historial: "Mis pagos"); sin él, la
  /// barra muestra el logotipo.
  final String? titulo;
  final String? servicioPorDefecto;
  final String? productoPorDefecto;
  final double? totalPorDefecto;

  /// Muestra el logotipo del proveedor en la tarjeta (historial).
  final bool mostrarLogo;

  /// Muestra el motivo amigable cuando la operación fue rechazada.
  final bool mostrarMotivo;

  /// Aviso sobre las acciones (historial: operación pendiente).
  final String? aviso;

  /// Acciones como accesos circulares; la principal en el color de marca.
  final List<AccionResultado> acciones;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final colores = ColoresApp.de(context);
    final c = comprobante;
    final exitoso = c.estatus == EstatusPago.confirmada;
    final servicio = c.servicio ?? servicioPorDefecto;
    final producto = c.producto ?? productoPorDefecto;
    final total = c.monto ?? totalPorDefecto;
    final operacion =
        c.tieneFolio ? c.folio : (c.identificadorOperacion ?? c.upc);
    final fecha = formatearFechaHistorial(c.fechaTransaccion);
    final tipo = tipoEstadoDe(c.estatus);
    final leyenda = exitoso ? c.leyenda : null;
    final pin = exitoso ? c.pinServicio : null;
    final nota = tema.textTheme.bodySmall;

    return PaginaFlujo(
      titulo: titulo,
      tituloDiscreto: true,
      marca: titulo == null,
      cabecera: CabeceraEstado(
        tipo: tipo,
        titulo: tituloEstado(c.estatus),
        etiquetaImporte: TextosPago.totalEtiqueta,
        importe: total == null ? '—' : formatearMoneda(total),
      ),
      children: [
        TarjetaDatos(
          relleno: const EdgeInsets.all(Esp.x6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Semantics(
                      header: true,
                      child: Text(
                        TextosPago.comprobanteTitulo,
                        style: tema.textTheme.titleMedium,
                      ),
                    ),
                  ),
                  const LogotipoBanGus(tamanoTexto: Escala.s16),
                ],
              ),
              if (mostrarLogo) ...[
                const SizedBox(height: Esp.x4),
                Row(
                  children: [
                    LogoServicio(
                      url: c.logoUrl,
                      nombre: servicio,
                      tamano: Medidas.logoLista,
                    ),
                    const SizedBox(width: Esp.x3),
                    Expanded(
                      child: Text(
                        servicio ?? '',
                        style: tema.textTheme.titleMedium,
                      ),
                    ),
                  ],
                ),
              ],
              const SizedBox(height: Esp.x3),
              FichaDatos(
                filas: [
                  FilaDato(TextosPago.servicio, servicio ?? '—'),
                  FilaDato(
                    TextosPago.producto,
                    (producto == null || producto.isEmpty) ? '—' : producto,
                  ),
                  if (c.telefonoEnmascarado != null)
                    FilaDato(TextosPago.telefono, c.telefonoEnmascarado!),
                  if (c.referenciaEnmascarada != null)
                    FilaDato(TextosPago.referencia, c.referenciaEnmascarada!),
                  if (c.numCuenta != null)
                    FilaDato(TextosPago.cuenta, c.numCuenta!),
                  if (fecha != null) FilaDato(TextosPago.fechaHora, fecha),
                  FilaDato(
                    TextosPago.estatus,
                    TextosPago.estatusTexto(c.estatus),
                  ),
                ],
              ),
              const Divider(),
              FilaCopiable(
                etiqueta: c.tieneFolio
                    ? TextosPago.folio
                    : TextosPago.numeroOperacion,
                valor: operacion ?? '—',
              ),
              if (pin != null) ...[
                const Divider(),
                FilaCopiable(etiqueta: TextosPago.codigoTarjeta, valor: pin),
              ],
              if (mostrarMotivo && c.estatus == EstatusPago.rechazada) ...[
                const SizedBox(height: Esp.x3),
                Text(TextosPago.motivoRechazo(c.motivo), style: nota),
              ],
              if (leyenda != null) ...[
                const SizedBox(height: Esp.x3),
                // Texto plano informativo: nunca se interpreta como HTML.
                Text(
                  leyenda,
                  style: nota?.copyWith(color: colores.textoSecundario),
                ),
              ],
            ],
          ),
        ),
        if (aviso != null) ...[
          const SizedBox(height: Esp.x6),
          BannerMensaje(
            tipo: TipoBanner.aviso,
            icono: Icons.schedule,
            mensaje: aviso!,
          ),
        ],
        if (acciones.isNotEmpty) ...[
          const SizedBox(height: Esp.x6),
          Wrap(
            spacing: Esp.x4,
            runSpacing: Esp.x2,
            children: [
              for (final a in acciones)
                AccesoCircular(
                  etiqueta: a.etiqueta,
                  icono: a.icono ?? Icons.arrow_forward,
                  principal: a.principal,
                  alTocar: a.alPulsar,
                ),
            ],
          ),
        ],
      ],
    );
  }
}

/// Dato copiable (`.comprobante__copiable` de la web): etiqueta y valor a la
/// izquierda, botón secundario "Copiar" que pasa a "Copiado".
class FilaCopiable extends StatefulWidget {
  const FilaCopiable({super.key, required this.etiqueta, required this.valor});

  final String etiqueta;
  final String valor;

  @override
  State<FilaCopiable> createState() => _FilaCopiableState();
}

class _FilaCopiableState extends State<FilaCopiable> {
  bool _copiado = false;

  Future<void> _copiar() async {
    try {
      await Clipboard.setData(ClipboardData(text: widget.valor));
      if (mounted) setState(() => _copiado = true);
    } catch (_) {
      if (mounted) setState(() => _copiado = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Esp.x3),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.etiqueta, style: tema.textTheme.bodySmall),
                const SizedBox(height: Esp.x1),
                SelectableText(
                  widget.valor,
                  style: estiloImporte(
                    tamano: Escala.s20,
                    color: tema.colorScheme.onSurface,
                  ),
                ),
              ],
            ),
          ),
          if (widget.valor != '—') ...[
            const SizedBox(width: Esp.x3),
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: Esp.x4),
                minimumSize: const Size(Medidas.tactil, Medidas.boton),
              ),
              onPressed: _copiar,
              icon: const Icon(Icons.copy_rounded, size: 18),
              label: Text(_copiado ? TextosPago.copiado : TextosPago.copiar),
            ),
          ],
        ],
      ),
    );
  }
}
