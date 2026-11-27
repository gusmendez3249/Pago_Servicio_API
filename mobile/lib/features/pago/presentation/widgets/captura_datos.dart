import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../../../../shared/widgets/pagina_flujo.dart';
import '../../domain/utils/reglas_pago.dart';
import '../textos_pago.dart';

/// Captura del teléfono dos veces (productos con tipoFront 1).
class CapturaTelefono extends StatefulWidget {
  const CapturaTelefono({
    super.key,
    required this.error,
    required this.alContinuar,
    this.cabecera,
    this.tuPago,
  });

  final ErrorTelefono? error;

  /// Tarjeta "Tu pago" con el resumen del servicio.
  final Widget? tuPago;

  /// Servicio e importe sobre la franja negra.
  final Widget? cabecera;
  final void Function(String telefono, String confirmacion) alContinuar;

  @override
  State<CapturaTelefono> createState() => _CapturaTelefonoState();
}

class _CapturaTelefonoState extends State<CapturaTelefono> {
  final TextEditingController _telefono = TextEditingController();
  final TextEditingController _confirmacion = TextEditingController();

  @override
  void dispose() {
    _telefono.dispose();
    _confirmacion.dispose();
    super.dispose();
  }

  void _enviar() => widget.alContinuar(_telefono.text, _confirmacion.text);

  @override
  Widget build(BuildContext context) {
    final error = widget.error;
    final textoError = error == null ? null : TextosPago.errorTelefono(error);
    return PaginaFlujo(
      cabecera: widget.cabecera,
      paso: 2,
      segundoPaso: 'Teléfono',
      pie: FilledButton(
        onPressed: _enviar,
        child: const Text(TextosPago.revisarPago),
      ),
      children: [
        TarjetaDatos(
          relleno: const EdgeInsets.all(Esp.x6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                controller: _telefono,
                keyboardType: TextInputType.phone,
                textInputAction: TextInputAction.next,
                maxLength: 10,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                style: estiloCampoCifras(),
                decoration: const InputDecoration(
                  labelText: TextosPago.telefonoEtiqueta,
                  hintText: '10 dígitos',
                  counterText: '',
                ),
              ),
              const SizedBox(height: Esp.x4),
              TextField(
                controller: _confirmacion,
                keyboardType: TextInputType.phone,
                textInputAction: TextInputAction.done,
                maxLength: 10,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                onSubmitted: (_) => _enviar(),
                style: estiloCampoCifras(),
                decoration: InputDecoration(
                  labelText: TextosPago.telefonoConfirmarEtiqueta,
                  counterText: '',
                  errorText: textoError,
                ),
              ),
            ],
          ),
        ),
        if (widget.tuPago != null) ...[
          const SizedBox(height: Esp.x4),
          widget.tuPago!,
        ],
      ],
    );
  }
}

/// Captura única del importe del recibo (verificación SOLO_FORMATO).
class CapturaImporte extends StatefulWidget {
  const CapturaImporte({
    super.key,
    required this.error,
    required this.alContinuar,
    this.cabecera,
    this.tuPago,
  });

  final ErrorImporte? error;

  /// Tarjeta "Tu pago" con el resumen del servicio.
  final Widget? tuPago;

  /// Servicio sobre la franja negra.
  final Widget? cabecera;
  final void Function(String importe) alContinuar;

  @override
  State<CapturaImporte> createState() => _CapturaImporteState();
}

class _CapturaImporteState extends State<CapturaImporte> {
  final TextEditingController _importe = TextEditingController();

  @override
  void dispose() {
    _importe.dispose();
    super.dispose();
  }

  void _enviar() => widget.alContinuar(_importe.text);

  @override
  Widget build(BuildContext context) {
    final error = widget.error;
    return PaginaFlujo(
      cabecera: widget.cabecera,
      paso: 3,
      pie: FilledButton(
        onPressed: _enviar,
        child: const Text(TextosPago.revisarPago),
      ),
      children: [
        TarjetaDatos(
          relleno: const EdgeInsets.all(Esp.x6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                controller: _importe,
                keyboardType:
                    const TextInputType.numberWithOptions(decimal: true),
                textInputAction: TextInputAction.done,
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'[0-9.]')),
                  LengthLimitingTextInputFormatter(10),
                ],
                onSubmitted: (_) => _enviar(),
                style: estiloCampoCifras(),
                decoration: InputDecoration(
                  labelText: TextosPago.importeEtiqueta,
                  prefixText: '\$ ',
                  hintText: '0.00',
                  errorText:
                      error == null ? null : TextosPago.errorImporte(error),
                ),
              ),
            ],
          ),
        ),
        if (widget.tuPago != null) ...[
          const SizedBox(height: Esp.x4),
          widget.tuPago!,
        ],
      ],
    );
  }
}
