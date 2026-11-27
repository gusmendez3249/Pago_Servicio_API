import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/router/app_router.dart';
import '../../../../shared/widgets/app_scaffold.dart';
import '../../domain/entities/comprobante_pago.dart';
import '../textos_pago.dart';
import '../widgets/comprobante_vista.dart';
import '../widgets/resultado_vista.dart';

/// Comprobante de un pago recién hecho. Folio, upc y código de tarjeta viven
/// solo aquí. Acciones como en la web: ir al inicio, ver mis pagos y volver
/// al resultado.
class ComprobanteScreen extends StatelessWidget {
  const ComprobanteScreen({
    super.key,
    required this.comprobante,
    this.servicio,
    this.producto,
    this.total,
  });

  final ComprobantePago comprobante;
  final String? servicio;
  final String? producto;
  final double? total;

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      cuerpo: ComprobanteVista(
        comprobante: comprobante,
        servicioPorDefecto: servicio,
        productoPorDefecto: producto,
        totalPorDefecto: total,
        mostrarMotivo: true,
        acciones: [
          AccionResultado(
            TextosPago.irInicio,
            () => context.go(Rutas.inicio),
            icono: Icons.home_outlined,
          ),
          AccionResultado(
            TextosPago.verMisPagos,
            () => context.go(Rutas.historial),
            principal: false,
            icono: Icons.receipt_long_outlined,
          ),
          AccionResultado(
            TextosPago.volverAlResultado,
            () => Navigator.of(context).maybePop(),
            principal: false,
            icono: Icons.arrow_back,
          ),
        ],
      ),
    );
  }
}
