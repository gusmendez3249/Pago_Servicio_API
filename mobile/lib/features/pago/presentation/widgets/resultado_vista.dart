import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../shared/widgets/estado_operacion.dart';
import '../../../../shared/widgets/ficha_datos.dart';
import '../../../../shared/widgets/pagina_flujo.dart';
import '../../../catalogo/presentation/widgets/estados_catalogo.dart';
import '../textos_pago.dart';

/// Acción de una pantalla de resultado.
class AccionResultado {
  const AccionResultado(
    this.etiqueta,
    this.alPulsar, {
    this.principal = true,
    this.icono,
  });

  final String etiqueta;
  final VoidCallback? alPulsar;
  final bool principal;

  /// Icono cuando la acción se muestra como acceso circular.
  final IconData? icono;
}

/// Acciones apiladas a ancho completo (`.acciones` de la web en móvil): la
/// principal en el color de marca y el resto como secundarias, en el orden dado.
class AccionesApiladas extends StatelessWidget {
  const AccionesApiladas({super.key, required this.acciones});

  final List<AccionResultado> acciones;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < acciones.length; i++) ...[
          if (i > 0) const SizedBox(height: Esp.x3),
          acciones[i].principal
              ? FilledButton(
                  onPressed: acciones[i].alPulsar,
                  child: Text(acciones[i].etiqueta),
                )
              : OutlinedButton(
                  onPressed: acciones[i].alPulsar,
                  child: Text(acciones[i].etiqueta),
                ),
        ],
      ],
    );
  }
}

/// Estado de la operación sobre la franja: icono de estado y título serif en
/// una fila y, si se conoce, la etiqueta y el importe de 40.
class CabeceraEstado extends StatelessWidget {
  const CabeceraEstado({
    super.key,
    required this.tipo,
    required this.titulo,
    this.importe,
    this.etiquetaImporte,
  });

  final TipoEstado tipo;
  final String titulo;
  final String? importe;
  final String? etiquetaImporte;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final i = importe;
    return Semantics(
      liveRegion: true,
      container: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconoEstado(tipo: tipo),
              const SizedBox(width: Esp.x4),
              Expanded(
                child: Text(
                  titulo,
                  style: estiloTitular(
                    tamano: Escala.s28,
                    color: colores.texto,
                  ),
                ),
              ),
            ],
          ),
          if (i != null) ...[
            const SizedBox(height: Esp.x6),
            if (etiquetaImporte != null)
              Padding(
                padding: const EdgeInsets.only(bottom: Esp.x1),
                child: Text(
                  etiquetaImporte!,
                  style: estiloTexto(
                    tamano: Escala.s14,
                    color: colores.textoSecundario,
                  ),
                ),
              ),
            Text(
              i,
              style: estiloImporte(tamano: Escala.s40, color: colores.texto),
            ),
          ],
        ],
      ),
    );
  }
}

/// Pantalla de resultado del pago, igual que la web: estado en la franja,
/// mensajes en una tarjeta (con "Consultando el estado…" mientras se sondea)
/// y las acciones apiladas debajo.
class ResultadoVista extends StatelessWidget {
  const ResultadoVista({
    super.key,
    required this.tipo,
    required this.titulo,
    required this.mensajes,
    required this.acciones,
    this.importe,
    this.etiquetaImporte,
    this.cargando = false,
  });

  final TipoEstado tipo;
  final String titulo;
  final List<String> mensajes;
  final List<AccionResultado> acciones;

  /// Importe formateado que se muestra grande bajo el título.
  final String? importe;
  final String? etiquetaImporte;

  /// Consulta de estado en curso (pendiente mientras se sondea).
  final bool cargando;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return PaginaFlujo(
      marca: true,
      cabecera: CabeceraEstado(
        tipo: tipo,
        titulo: titulo,
        importe: importe,
        etiquetaImporte: etiquetaImporte,
      ),
      children: [
        TarjetaDatos(
          relleno: const EdgeInsets.all(Esp.x6),
          child: Semantics(
            liveRegion: true,
            container: true,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (var k = 0; k < mensajes.length; k++) ...[
                  if (k > 0) const SizedBox(height: Esp.x3),
                  Text(
                    mensajes[k],
                    style: tema.textTheme.bodyLarge?.copyWith(
                      fontFeatures: kCifras,
                    ),
                  ),
                ],
                if (cargando) ...[
                  const SizedBox(height: Esp.x3),
                  const LineaCargando(texto: TextosPago.consultandoEstado),
                ],
              ],
            ),
          ),
        ),
        if (acciones.isNotEmpty) ...[
          const SizedBox(height: Esp.x6),
          AccionesApiladas(acciones: acciones),
        ],
      ],
    );
  }
}
