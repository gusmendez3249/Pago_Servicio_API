import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../core/config/app_theme.dart';
import '../../../catalogo/domain/utils/formato_catalogo.dart';
import '../../domain/utils/reglas_pago.dart';
import '../textos_pago.dart';

/// Muestra la hoja inferior de confirmación con el PIN. Devuelve el PIN de 4
/// dígitos, o null si se cancela. El PIN solo existe en el campo y en este
/// valor de retorno: no se guarda en ningún estado.
Future<String?> mostrarDialogoPin(
  BuildContext context, {
  required double total,
  String? aviso,
}) {
  return showModalBottomSheet<String>(
    context: context,
    isDismissible: false,
    enableDrag: false,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => DialogoPin(total: total, aviso: aviso),
  );
}

class DialogoPin extends StatefulWidget {
  const DialogoPin({super.key, required this.total, this.aviso});

  final double total;
  final String? aviso;

  @override
  State<DialogoPin> createState() => _DialogoPinState();
}

class _DialogoPinState extends State<DialogoPin> {
  final TextEditingController _controlador = TextEditingController();

  @override
  void dispose() {
    _controlador.clear();
    _controlador.dispose();
    super.dispose();
  }

  bool get _completo => pinValido(_controlador.text);

  void _confirmar() {
    if (!_completo) return;
    final pin = _controlador.text;
    // El controlador se limpia en cada intento.
    _controlador.clear();
    Navigator.of(context).pop(pin);
  }

  void _cancelar() {
    _controlador.clear();
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final aviso = widget.aviso;
    final total = formatearMoneda(widget.total);
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.viewInsetsOf(context).bottom,
      ),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(
            Esp.x6,
            Esp.x3,
            Esp.x6,
            Esp.x4,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: Esp.x3),
              Semantics(
                header: true,
                child: Text(
                  TextosPago.confirmaTuPago,
                  style: tema.textTheme.titleLarge,
                ),
              ),
              const SizedBox(height: Esp.x4),
              Text(
                TextosPago.vasAPagar,
                style: tema.textTheme.bodyMedium?.copyWith(
                  color: tema.colorScheme.onSurfaceVariant,
                ),
              ),
              Text(
                total,
                style: estiloImporte(
                  tamano: Escala.s40,
                  color: tema.colorScheme.onSurface,
                ),
              ),
              const SizedBox(height: Esp.x6),
              TextField(
                controller: _controlador,
                autofocus: true,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 4,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                autocorrect: false,
                enableSuggestions: false,
                textAlign: TextAlign.center,
                style: estiloTexto(
                  tamano: Escala.s28,
                  peso: FontWeight.w600,
                  color: tema.colorScheme.onSurface,
                  cifras: true,
                  espaciado: 12,
                ),
                onChanged: (_) => setState(() {}),
                onSubmitted: (_) => _confirmar(),
                decoration: InputDecoration(
                  labelText: TextosPago.ingresaPin,
                  counterText: '',
                  // Igual que la web: el aviso del PIN va junto al campo.
                  errorText: aviso,
                  errorMaxLines: 3,
                ),
              ),
              const SizedBox(height: Esp.x6),
              FilledButton(
                onPressed: _completo ? _confirmar : null,
                child: const Text(TextosPago.confirmarPago),
              ),
              const SizedBox(height: Esp.x3),
              OutlinedButton(
                onPressed: _cancelar,
                child: const Text(TextosPago.cancelar),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
