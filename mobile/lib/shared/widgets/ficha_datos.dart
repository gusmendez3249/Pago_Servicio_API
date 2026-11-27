import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';

/// Una fila etiqueta / valor de una ficha de datos.
class FilaDato {
  const FilaDato(
    this.etiqueta,
    this.valor, {
    this.color,
    this.fuerte = false,
    this.accion,
  });

  final String etiqueta;
  final String valor;

  /// Color del valor (p. ej. el del estado).
  final Color? color;
  final bool fuerte;

  /// Control al final de la fila (p. ej. copiar el folio).
  final Widget? accion;
}

/// Ficha de datos: filas etiqueta (izquierda) y valor (derecha, cifras
/// tabulares) separadas por líneas de 1 px. El [total] va en fila propia con
/// el valor grande.
class FichaDatos extends StatelessWidget {
  const FichaDatos({super.key, required this.filas, this.total});

  final List<FilaDato> filas;
  final FilaDato? total;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final hijos = <Widget>[];
    for (var i = 0; i < filas.length; i++) {
      if (i > 0) hijos.add(const Divider());
      hijos.add(_FilaFicha(fila: filas[i]));
    }
    final t = total;
    if (t != null) {
      if (filas.isNotEmpty) hijos.add(const Divider());
      hijos.add(
        Padding(
          padding: const EdgeInsets.symmetric(vertical: Esp.x3),
          child: Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: Esp.x3,
            children: [
              Text(t.etiqueta, style: tema.textTheme.titleMedium),
              Text(
                t.valor,
                style: estiloImporte(
                  tamano: Escala.s28,
                  color: t.color ?? tema.colorScheme.onSurface,
                ),
              ),
            ],
          ),
        ),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: hijos,
    );
  }
}

class _FilaFicha extends StatelessWidget {
  const _FilaFicha({required this.fila});

  final FilaDato fila;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final accion = fila.accion;
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: Medidas.tactil),
      child: Padding(
        padding: EdgeInsets.symmetric(
          vertical: accion == null ? Esp.x3 : Esp.x1,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Expanded(
              flex: 2,
              child: Text(
                fila.etiqueta,
                style: tema.textTheme.bodyMedium?.copyWith(
                  color: tema.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            const SizedBox(width: Esp.x3),
            Expanded(
              flex: 3,
              child: Text(
                fila.valor,
                textAlign: TextAlign.end,
                style: tema.textTheme.bodyLarge?.copyWith(
                  color: fila.color,
                  height: 1.3,
                  fontWeight: fila.fuerte ? FontWeight.w700 : FontWeight.w500,
                  fontFeatures: kCifras,
                ),
              ),
            ),
            if (accion != null) accion,
          ],
        ),
      ),
    );
  }
}

/// Tarjeta (superficie 2, radio 8, borde acero de 1 px) que agrupa una ficha de
/// datos u otro contenido.
class TarjetaDatos extends StatelessWidget {
  const TarjetaDatos({
    super.key,
    required this.child,
    this.relleno = const EdgeInsets.symmetric(
      horizontal: Esp.x4,
      vertical: Esp.x1,
    ),
  });

  final Widget child;
  final EdgeInsetsGeometry relleno;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colores.superficie,
        border: Border.all(color: colores.linea),
        borderRadius: BorderRadius.circular(Radios.tarjeta),
      ),
      child: Padding(padding: relleno, child: child),
    );
  }
}

/// Encabezado de una sección de la hoja.
class TituloSeccion extends StatelessWidget {
  const TituloSeccion(
    this.texto, {
    super.key,
    this.detalle,
    this.discreto = false,
  });

  final String texto;
  final String? detalle;

  /// Etiqueta pequeña (p. ej. la fecha que agrupa movimientos) en lugar del
  /// título serif de sección.
  final bool discreto;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final d = detalle;
    if (discreto) {
      // Fecha que agrupa movimientos: secundario 14/600 (`.historial__fecha`).
      return Semantics(
        header: true,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(Esp.x1, 0, Esp.x1, Esp.x2),
          child: Text(
            texto,
            style: tema.textTheme.labelLarge?.copyWith(
              color: tema.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      );
    }
    // Título de sección a la izquierda y detalle a la derecha
    // (`.catalogo__encabezado`).
    return Padding(
      padding: const EdgeInsets.only(bottom: Esp.x3),
      child: Wrap(
        alignment: WrapAlignment.spaceBetween,
        crossAxisAlignment: WrapCrossAlignment.end,
        spacing: Esp.x4,
        runSpacing: Esp.x1,
        children: [
          Semantics(
            header: true,
            child: Text(texto, style: tema.textTheme.titleMedium),
          ),
          if (d != null)
            Text(
              d,
              style: tema.textTheme.bodySmall?.copyWith(fontFeatures: kCifras),
            ),
        ],
      ),
    );
  }
}

/// Un renglón de una lista agrupada en tarjeta (radio 16, línea de 1 px).
/// Permite construir la tarjeta de forma perezosa en un `SliverList`: el
/// primero redondea arriba, el último abajo y los intermedios llevan una
/// línea divisoria con sangría [sangria].
class SegmentoTarjeta extends StatelessWidget {
  const SegmentoTarjeta({
    super.key,
    required this.primero,
    required this.ultimo,
    required this.child,
    this.sangria = Esp.x4,
  });

  final bool primero;
  final bool ultimo;
  final double sangria;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final linea = BorderSide(color: colores.linea);
    const r = Radius.circular(Radios.tarjeta);
    final radio = BorderRadius.vertical(
      top: primero ? r : Radius.zero,
      bottom: ultimo ? r : Radius.zero,
    );
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: Medidas.anchoContenido),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: Esp.x4),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: colores.superficie,
              borderRadius: radio,
              border: Border(
                left: linea,
                right: linea,
                top: primero ? linea : BorderSide.none,
                bottom: ultimo ? linea : BorderSide.none,
              ),
            ),
            child: ClipRRect(
              borderRadius: radio,
              child: Material(
                type: MaterialType.transparency,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (!primero) Divider(indent: sangria),
                    child,
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Lista agrupada en tarjeta, construida de forma perezosa.
class SliverTarjeta extends StatelessWidget {
  const SliverTarjeta({
    super.key,
    required this.cantidad,
    required this.constructor,
    this.sangria = Esp.x4,
  });

  final int cantidad;
  final IndexedWidgetBuilder constructor;
  final double sangria;

  @override
  Widget build(BuildContext context) {
    return SliverList.builder(
      itemCount: cantidad,
      itemBuilder: (context, i) => SegmentoTarjeta(
        primero: i == 0,
        ultimo: i == cantidad - 1,
        sangria: sangria,
        child: constructor(context, i),
      ),
    );
  }
}
