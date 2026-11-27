import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/config/app_theme.dart';
import '../../core/router/app_router.dart';
import 'barra_secciones.dart';
import 'contenedor_ancho.dart';
import 'logo_bangus.dart';

/// Scaffold de las pantallas protegidas: fondo negro, barra de estado clara
/// sobre la cabecera negra y, en las secciones principales, la barra
/// inferior.
///
/// [cuerpo] suele ser un [MarcoBanda] (o una [PaginaFlujo], que lo usa).
class AppScaffold extends StatelessWidget {
  const AppScaffold({super.key, required this.cuerpo, this.seccion});

  final Widget cuerpo;

  /// Sección activa de la barra inferior; null la oculta (flujo de pago y
  /// detalles).
  final SeccionApp? seccion;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final s = seccion;
    // Con el teclado abierto la barra inferior solo quitaría espacio.
    final teclado = MediaQuery.viewInsetsOf(context).bottom > 0;
    return Scaffold(
      backgroundColor: colores.fondo,
      body: cuerpo,
      bottomNavigationBar:
          (s == null || teclado) ? null : BarraSecciones(actual: s),
    );
  }
}

/// Estructura de las pantallas: cabecera negra brillosa (barra superior con
/// volver, título serif y acciones; opcionalmente los [pasos] y el dato o
/// acción principal, [cabecera]) que termina en línea recta; debajo, el
/// contenido sobre el fondo negro y, anclado abajo, el [pie] con el botón primario.
class MarcoBanda extends StatelessWidget {
  const MarcoBanda({
    super.key,
    this.titulo,
    this.tituloDiscreto = false,
    this.marca = false,
    this.acciones = const <Widget>[],
    this.pasos,
    this.cancelar = false,
    this.cabecera,
    required this.cuerpo,
    this.pie,
  });

  /// Título de la barra superior.
  final String? titulo;

  /// El título de la barra va como etiqueta (Franklin) porque el título
  /// serif de la pantalla está en la [cabecera].
  final bool tituloDiscreto;

  /// Muestra el logotipo en lugar del título (secciones principales).
  final bool marca;
  final List<Widget> acciones;

  /// Acción "Cancelar" del flujo de pago (texto subrayado a la derecha de
  /// la barra), que vuelve al inicio.
  final bool cancelar;

  /// Indicador de pasos del flujo, dentro de la cabecera negra.
  final Widget? pasos;

  /// Dato o acción principal sobre la cabecera negra.
  final Widget? cabecera;

  /// Contenido; debe manejar su propio desplazamiento.
  final Widget cuerpo;

  /// Acciones ancladas al pie.
  final Widget? pie;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final alto = MediaQuery.sizeOf(context).height;
    final c = cabecera;
    final ps = pasos;
    final p = pie;
    return AnnotatedRegion(
      value: kBarraEstadoSobreNegro,
      child: ColoredBox(
        color: colores.fondo,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Barra y franja forman una sola pieza de metal negro: filo de
            // luz bajo la barra de estado, divisor interno y filo de sombra
            // abajo. Nunca ocupa más de la mitad del alto: con texto grande
            // se desplaza en lugar de desbordarse.
            SobreNegro(
              child: DecoratedBox(
                decoration: superficieMetal(filoArriba: false),
                child: ConstrainedBox(
                  constraints: BoxConstraints(maxHeight: alto * 0.5),
                  child: SingleChildScrollView(
                    physics: const ClampingScrollPhysics(),
                    child: SafeArea(
                      bottom: false,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const ColoredBox(
                            color: Paleta.filoLuz,
                            child: SizedBox(height: 1),
                          ),
                          ContenedorAncho(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                BarraSuperior(
                                  titulo: titulo,
                                  discreto: tituloDiscreto,
                                  marca: marca,
                                  acciones: [
                                    ...acciones,
                                    if (cancelar)
                                      TextButton(
                                        onPressed: () =>
                                            context.go(Rutas.catalogo),
                                        child: const Text('Cancelar'),
                                      ),
                                  ],
                                  // Sin título, los pasos van en la misma
                                  // fila que la flecha de regreso.
                                  centro: titulo == null ? ps : null,
                                ),
                                if (ps != null && titulo != null)
                                  Padding(
                                    padding: const EdgeInsets.fromLTRB(
                                      Esp.x4,
                                      Esp.x1,
                                      Esp.x4,
                                      Esp.x3,
                                    ),
                                    child: ps,
                                  ),
                              ],
                            ),
                          ),
                          if (c != null) ...[
                            const ColoredBox(
                              color: Paleta.lineaSobreNegro,
                              child: SizedBox(height: 1),
                            ),
                            ContenedorAncho(
                              child: Padding(
                                padding: const EdgeInsets.fromLTRB(
                                  Esp.x4,
                                  Esp.x6,
                                  Esp.x4,
                                  Esp.x6,
                                ),
                                child: SizedBox(
                                  width: double.infinity,
                                  child: c,
                                ),
                              ),
                            ),
                          ] else
                            const SizedBox(height: Esp.x2),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
            Expanded(child: cuerpo),
            if (p != null) PieAcciones(child: p),
          ],
        ),
      ),
    );
  }
}

/// Barra superior sobre la cabecera negra: volver (si la ruta lo permite),
/// título serif o logotipo, y acciones.
class BarraSuperior extends StatelessWidget {
  const BarraSuperior({
    super.key,
    this.titulo,
    this.discreto = false,
    this.marca = false,
    this.acciones = const <Widget>[],
    this.centro,
  });

  final String? titulo;
  final bool discreto;

  /// Contenido de la fila cuando no hay título (p. ej. los pasos).
  final Widget? centro;
  final bool marca;
  final List<Widget> acciones;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final puedeVolver = ModalRoute.of(context)?.impliesAppBarDismissal ?? false;
    final t = titulo;
    return IconButtonTheme(
      data: IconButtonThemeData(
        style: IconButton.styleFrom(
          foregroundColor: colores.texto,
          minimumSize: const Size(Medidas.tactil, Medidas.tactil),
          focusColor: colores.lineaFuerte,
          highlightColor: colores.superficie,
        ).copyWith(
          side: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.focused)
                ? BorderSide(color: colores.texto)
                : null,
          ),
        ),
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 56),
        child: Row(
          children: [
            if (puedeVolver)
              Padding(
                padding: const EdgeInsets.only(left: Esp.x1),
                child: IconButton(
                  onPressed: () => Navigator.of(context).maybePop(),
                  icon: const Icon(Icons.arrow_back),
                  tooltip: 'Volver',
                ),
              )
            else
              const SizedBox(width: Esp.x4),
            Expanded(
              child: marca
                  ? const Align(
                      alignment: Alignment.centerLeft,
                      child: LogotipoBanGus(),
                    )
                  : (t == null
                      ? Padding(
                          padding: const EdgeInsets.only(right: Esp.x4),
                          child: centro ?? const SizedBox.shrink(),
                        )
                      : Semantics(
                          header: true,
                          child: Text(
                            t,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: discreto
                                ? estiloTexto(
                                    tamano: Escala.s16,
                                    peso: FontWeight.w600,
                                    color: colores.texto,
                                  )
                                : estiloTitular(
                                    tamano: Escala.s20,
                                    color: colores.texto,
                                  ),
                          ),
                        )),
            ),
            ...acciones,
            const SizedBox(width: Esp.x1),
          ],
        ),
      ),
    );
  }
}

/// Pie anclado: botón primario de ancho completo (y, si hace falta, una
/// acción secundaria).
class PieAcciones extends StatelessWidget {
  const PieAcciones({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colores.fondo,
        border: Border(top: BorderSide(color: colores.linea)),
      ),
      child: SafeArea(
        top: false,
        child: ContenedorAncho(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(
              Esp.x4,
              Esp.x3,
              Esp.x4,
              Esp.x3,
            ),
            // El botón primario ocupa todo el ancho.
            child: SizedBox(width: double.infinity, child: child),
          ),
        ),
      ),
    );
  }
}
