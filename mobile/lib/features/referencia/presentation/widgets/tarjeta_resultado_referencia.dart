import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../shared/widgets/banner_mensaje.dart';
import '../../domain/entities/validacion_referencia.dart';
import '../providers/referencia_providers.dart';
import '../textos_referencia.dart';

bool _esSoloFormato(ReferenciaState vm) =>
    vm.validacion?.verificacion == VerificacionReferencia.soloFormato;

/// Texto de una situación conocida con su monto (valor absoluto).
String? _textoPorSituacion(SituacionSaldo? situacion, double? saldo) {
  switch (situacion) {
    case SituacionSaldo.adeudo:
      return saldo == null ? null : TextosReferencia.adeudo(saldo);
    case SituacionSaldo.saldoAFavor:
      return saldo == null ? null : TextosReferencia.saldoAFavor(saldo);
    case SituacionSaldo.sinAdeudo:
      return TextosReferencia.sinAdeudo;
    case SituacionSaldo.desconocida:
    case null:
      return null;
  }
}

/// Texto principal del resultado para [vm]; solo textos de la especificación.
/// Devuelve null en los estados que no muestran tarjeta.
String? textoResultadoReferencia(ReferenciaState vm) {
  switch (vm.estado) {
    case EstadoReferencia.valida:
      if (_esSoloFormato(vm)) return TextosReferencia.soloFormato;
      final vv = vm.validacion;
      return _textoPorSituacion(vv?.situacionEfectiva, vv?.saldo) ??
          TextosReferencia.error;
    case EstadoReferencia.soloConsulta:
      final vc = vm.validacion;
      return _textoPorSituacion(vc?.situacionEfectiva, vc?.saldo) ??
          TextosReferencia.soloConsulta;
    case EstadoReferencia.invalida:
      return TextosReferencia.invalida;
    case EstadoReferencia.error:
      return TextosReferencia.error;
    case EstadoReferencia.expirada:
      return TextosReferencia.expirada;
    case EstadoReferencia.capturando:
    case EstadoReferencia.formatoInvalido:
    case EstadoReferencia.validando:
      return null;
  }
}

/// Líneas que van debajo del texto principal.
List<String> lineasAdicionalesReferencia(ReferenciaState vm) {
  switch (vm.estado) {
    case EstadoReferencia.valida:
      if (_esSoloFormato(vm)) return const <String>[];
      final monto = vm.datosPago?.montoAPagar;
      return monto == null ? const [] : [TextosReferencia.totalAPagar(monto)];
    case EstadoReferencia.soloConsulta:
      final situacion = vm.validacion?.situacionEfectiva;
      if (situacion == SituacionSaldo.adeudo) {
        return const [TextosReferencia.soloConsulta];
      }
      if (situacion == SituacionSaldo.sinAdeudo ||
          situacion == SituacionSaldo.saldoAFavor) {
        return const [TextosReferencia.nadaPorPagar];
      }
      return const [];
    case EstadoReferencia.invalida:
    case EstadoReferencia.error:
    case EstadoReferencia.expirada:
    case EstadoReferencia.capturando:
    case EstadoReferencia.formatoInvalido:
    case EstadoReferencia.validando:
      return const [];
  }
}

/// Aviso de estado con el resultado de la validación (borde izquierdo y
/// fondo tenue del color del estado).
class TarjetaResultadoReferencia extends StatelessWidget {
  const TarjetaResultadoReferencia({super.key, required this.vm});

  final ReferenciaState vm;

  @override
  Widget build(BuildContext context) {
    final texto = textoResultadoReferencia(vm);
    if (texto == null) return const SizedBox.shrink();

    final tema = Theme.of(context);

    final TipoBanner tipo;
    switch (vm.estado) {
      // Mismos tonos que la web.
      case EstadoReferencia.valida:
        tipo = _esSoloFormato(vm) ? TipoBanner.aviso : TipoBanner.exito;
      case EstadoReferencia.soloConsulta:
        tipo = TipoBanner.info;
      case EstadoReferencia.expirada:
        tipo = TipoBanner.neutro;
      case EstadoReferencia.error:
        tipo = TipoBanner.aviso;
      case EstadoReferencia.invalida:
      case EstadoReferencia.capturando:
      case EstadoReferencia.formatoInvalido:
      case EstadoReferencia.validando:
        tipo = TipoBanner.error;
    }

    final lineas = lineasAdicionalesReferencia(vm);

    return AvisoEstado(
      tipo: tipo,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(texto, style: tema.textTheme.titleMedium),
          for (final linea in lineas) ...[
            const SizedBox(height: Esp.x1),
            Text(
              linea,
              style: tema.textTheme.bodyMedium?.copyWith(fontFeatures: kCifras),
            ),
          ],
        ],
      ),
    );
  }
}
