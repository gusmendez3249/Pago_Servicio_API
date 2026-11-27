import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';

/// Marca de BanGus: una "B" de plantilla formada por una columna (el pilar de
/// un banco) y dos bloques apilados, en formas sólidas sobre un lienzo de 64.
/// Es la misma de `web/public/marca/isotipo*.svg`. Va sin cuadro de fondo y
/// ajustada a su contorno (sin el aire del lienzo de 64), para que su borde
/// izquierdo coincida con el margen de la pantalla.
class LogoBanGus extends StatelessWidget {
  const LogoBanGus({super.key, this.tamano = 40, this.color});

  /// Alto de la marca.
  final double tamano;

  /// Color de la marca; por omisión, el del texto actual.
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ??
        DefaultTextStyle.of(context).style.color ??
        ColoresApp.de(context).texto;
    return CustomPaint(
      size: Size(tamano * PintorMarca.ancho / PintorMarca.alto, tamano),
      painter: PintorMarca(color: c),
    );
  }
}

/// Dibuja la marca rellena (coordenadas del SVG de 64).
class PintorMarca extends CustomPainter {
  const PintorMarca({required this.color});

  final Color color;

  /// Contorno de la marca en el lienzo de 64: x 16–47.5, y 12–52.
  static const double izquierda = 16;
  static const double arriba = 12;
  static const double ancho = 31.5;
  static const double alto = 40;

  /// `M16 12h9v40h-9Z M28 12h5a9.5 9.5 0 0 1 0 19h-5Z
  /// M28 33h10a9.5 9.5 0 0 1 0 19H28Z`
  static Path trazo() {
    const radio = Radius.circular(9.5);
    return Path()
      // Columna.
      ..addRect(const Rect.fromLTRB(16, 12, 25, 52))
      // Bloque superior.
      ..moveTo(28, 12)
      ..lineTo(33, 12)
      ..arcToPoint(const Offset(33, 31), radius: radio)
      ..lineTo(28, 31)
      ..close()
      // Bloque inferior.
      ..moveTo(28, 33)
      ..lineTo(38, 33)
      ..arcToPoint(const Offset(38, 52), radius: radio)
      ..lineTo(28, 52)
      ..close();
  }

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.height / alto);
    canvas.translate(-izquierda, -arriba);
    canvas.drawPath(trazo(), Paint()..color = color);
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant PintorMarca anterior) => anterior.color != color;
}

/// Logotipo: marca + "BanGus" en Source Serif 4 600 (interletrado −0.015 em).
/// La marca mide 1.25 × el tamaño del texto y la separación es 0.18 × el
/// tamaño del texto; ambos centrados verticalmente.
class LogotipoBanGus extends StatelessWidget {
  const LogotipoBanGus({
    super.key,
    this.tamanoTexto = Escala.s20,
  });

  final double tamanoTexto;

  @override
  Widget build(BuildContext context) {
    final c = ColoresApp.de(context);
    final color = c.texto;
    return Semantics(
      label: 'BanGus',
      header: true,
      child: ExcludeSemantics(
        child: Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            LogoBanGus(tamano: tamanoTexto * 1.25, color: color),
            SizedBox(width: tamanoTexto * 0.18),
            Flexible(
              child: Text(
                'BanGus',
                style: estiloTitular(tamano: tamanoTexto, color: color)
                    .copyWith(letterSpacing: -tamanoTexto * 0.015),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
