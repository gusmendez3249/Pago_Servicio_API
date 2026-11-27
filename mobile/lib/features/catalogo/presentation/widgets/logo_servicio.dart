import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';
import '../../domain/utils/formato_catalogo.dart';

bool _urlUsable(String? url) => url != null && url.trim().isNotEmpty;

/// Logotipo del servicio. Los logos de los proveedores se diseñan para fondo
/// claro: van sobre una placa blanca con borde.
/// Mientras carga muestra un placeholder del mismo tamaño; si la URL es nula
/// o falla, las iniciales en un círculo con borde.
class LogoServicio extends StatelessWidget {
  const LogoServicio({
    super.key,
    required this.url,
    required this.nombre,
    this.tamano = 56,
  });

  final String? url;
  final String? nombre;
  final double tamano;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    // Igual que la web: círculo; logo sobre placa blanca con 16 % de aire;
    // respaldo con iniciales en el color principal.
    Widget avatar() => Container(
          width: tamano,
          height: tamano,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: colores.circulo,
            border: Border.all(color: colores.linea),
            shape: BoxShape.circle,
          ),
          child: Text(
            iniciales(nombre),
            style: estiloTexto(
              color: colores.texto,
              peso: FontWeight.w700,
              tamano: tamano * 0.36,
            ),
          ),
        );

    Widget placeholder() => Container(
          width: tamano,
          height: tamano,
          decoration: BoxDecoration(
            color: colores.linea,
            shape: BoxShape.circle,
          ),
        );

    final Widget contenido;
    if (!_urlUsable(url)) {
      contenido = avatar();
    } else {
      contenido = Container(
        width: tamano,
        height: tamano,
        padding: EdgeInsets.all(tamano * 0.16),
        decoration: BoxDecoration(
          color: Paleta.blanco,
          border: Border.all(color: colores.linea),
          shape: BoxShape.circle,
        ),
        child: ClipOval(
          child: Image.network(
            url!.trim(),
            // En navegador el proveedor no envía cabeceras CORS: se usa un <img> como respaldo
            webHtmlElementStrategy: WebHtmlElementStrategy.fallback,
            fit: BoxFit.contain,
            loadingBuilder: (context, child, progreso) =>
                progreso == null ? child : placeholder(),
            errorBuilder: (context, error, stack) => avatar(),
          ),
        ),
      );
    }
    return Semantics(
      image: true,
      label:
          'Logotipo de ${(nombre ?? '').trim().isEmpty ? 'servicio' : nombre}',
      child: ExcludeSemantics(child: contenido),
    );
  }
}

/// Imagen de ayuda con placeholder mientras carga y mensaje si falla.
class ImagenAyuda extends StatelessWidget {
  const ImagenAyuda({super.key, required this.url});

  final String url;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);

    Widget caja(Widget hijo) => Container(
          height: 200,
          width: double.infinity,
          alignment: Alignment.center,
          padding: const EdgeInsets.all(Esp.x4),
          decoration: BoxDecoration(
            color: colores.linea,
            borderRadius: BorderRadius.circular(Radios.tarjeta),
          ),
          child: hijo,
        );

    return Semantics(
      image: true,
      label: 'Imagen de ayuda para ubicar la referencia',
      child: ClipRRect(
        borderRadius: BorderRadius.circular(Radios.tarjeta),
        child: Image.network(
          url.trim(),
          webHtmlElementStrategy: WebHtmlElementStrategy.fallback,
          width: double.infinity,
          fit: BoxFit.contain,
          loadingBuilder: (context, child, progreso) => progreso == null
              ? child
              : caja(const CircularProgressIndicator(strokeWidth: 2)),
          errorBuilder: (context, error, stack) => caja(
            const Text(
              'No se pudo cargar la imagen de ayuda.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ),
    );
  }
}

/// Indica si [url] puede intentarse cargar como imagen.
bool urlDeImagenUsable(String? url) => _urlUsable(url);
