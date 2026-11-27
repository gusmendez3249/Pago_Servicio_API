import 'package:flutter/material.dart';

import '../../core/config/app_theme.dart';

/// Acceso circular (`.acceso` y `.acceso-accion` de la web): círculo de 56
/// con icono de 24 y etiqueta de 13 debajo, en un ancho de 88. Sin colores
/// por categoría: círculo neutro con borde e icono en el color principal;
/// seleccionado o principal, círculo de marca con el icono invertido.
/// Funciona igual sobre claro y dentro de [SobreNegro].
class AccesoCircular extends StatelessWidget {
  const AccesoCircular({
    super.key,
    required this.etiqueta,
    required this.icono,
    required this.alTocar,
    this.activo = false,
    this.principal = false,
    this.semantica,
  });

  final String etiqueta;
  final IconData icono;

  /// Null lo deshabilita.
  final VoidCallback? alTocar;
  final bool activo;
  final bool principal;

  /// Etiqueta accesible si difiere del texto visible.
  final String? semantica;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final habilitado = alTocar != null;
    final resaltado = (activo || principal) && habilitado;
    final colorIcono = !habilitado
        ? colores.deshabilitado
        : (resaltado ? colores.sobreMarca : colores.texto);
    return Semantics(
      button: true,
      enabled: habilitado,
      selected: activo,
      label: semantica ?? etiqueta,
      child: ExcludeSemantics(
        child: InkWell(
          onTap: alTocar,
          borderRadius: BorderRadius.circular(Radios.campo),
          focusColor: colores.linea,
          highlightColor: colores.linea,
          child: SizedBox(
            width: 88,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(0, Esp.x1, 0, Esp.x2),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: Medidas.acceso,
                    height: Medidas.acceso,
                    decoration: BoxDecoration(
                      color: resaltado ? colores.marca : colores.circulo,
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: resaltado ? colores.marca : colores.linea,
                      ),
                    ),
                    child: Icon(icono, color: colorIcono),
                  ),
                  const SizedBox(height: Esp.x1),
                  Text(
                    etiqueta,
                    textAlign: TextAlign.center,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: estiloTexto(
                      tamano: Escala.s13,
                      altura: 1.25,
                      peso: activo ? FontWeight.w600 : FontWeight.w500,
                      color: !habilitado
                          ? colores.deshabilitado
                          : (activo || principal
                              ? colores.texto
                              : colores.textoSecundario),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
