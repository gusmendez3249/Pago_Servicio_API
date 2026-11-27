import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';
import 'logo_bangus.dart';

/// Rutas de las fotos de los banners (declaradas en `pubspec.yaml`).
abstract final class FotosBanner {
  static const String login = 'assets/banners/login.jpg';
  static const String inicio = 'assets/banners/inicio.jpg';
}

/// Foto de banner con proporción fija, `BoxFit.cover` y etiqueta semántica.
/// Si la imagen falta o no carga, muestra un respaldo diseñado (negro
/// brilloso con la marca a gran tamaño recortada).
class ImagenBanner extends StatelessWidget {
  const ImagenBanner({
    super.key,
    required this.asset,
    required this.semantica,
    this.proporcion = 16 / 7,
    this.altoMaximo = 180,
    this.alineacion = Alignment.center,
    this.alto,
  });

  final String asset;

  /// Descripción de la foto para lectores de pantalla.
  final String semantica;

  /// Ancho / alto.
  final double proporcion;
  final double altoMaximo;

  /// Encuadre (hacia las caras).
  final Alignment alineacion;

  /// Alto fijo; si se da, ignora [proporcion] y [altoMaximo].
  final double? alto;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, restricciones) {
        final ancho = restricciones.maxWidth;
        final h = alto ?? (ancho / proporcion).clamp(0, altoMaximo).toDouble();
        return Semantics(
          image: true,
          label: semantica,
          child: ExcludeSemantics(
            child: SizedBox(
              width: ancho,
              height: h,
              child: Image.asset(
                asset,
                fit: BoxFit.cover,
                alignment: alineacion,
                errorBuilder: (context, error, pila) => const RespaldoBanner(),
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Respaldo de un banner sin foto: superficie negra de marca con la marca de BanGus a
/// gran tamaño, recortada hacia la esquina inferior derecha.
class RespaldoBanner extends StatelessWidget {
  const RespaldoBanner({super.key});

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: superficieMetal(),
      child: LayoutBuilder(
        builder: (context, r) {
          final lado = r.maxHeight * 1.6;
          return ClipRect(
            child: Stack(
              children: [
                Positioned(
                  right: -lado * 0.18,
                  bottom: -lado * 0.3,
                  child: LogoBanGus(
                    tamano: lado,
                    color: Paleta.lineaSobreNegro,
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
