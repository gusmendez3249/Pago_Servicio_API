import 'package:flutter/material.dart';

import '../../../../core/config/app_theme.dart';
import '../../../../shared/widgets/acceso_circular.dart';
import '../../domain/utils/filtros_catalogo.dart';
import 'categorias_visuales.dart';
import '../../../../shared/entrada/entrada.dart';

/// Accesos rápidos por categoría: iconos en círculo con su nombre, con
/// desplazamiento horizontal. "Todas" + las categorías reales.
class FiltrosCategoria extends StatelessWidget {
  const FiltrosCategoria({
    super.key,
    required this.categorias,
    required this.total,
    required this.seleccionada,
    required this.alSeleccionar,
  });

  final List<CategoriaConteo> categorias;
  final int total;

  /// Null equivale a "Todas".
  final String? seleccionada;
  final ValueChanged<String?> alSeleccionar;

  @override
  Widget build(BuildContext context) {
    // Fila deslizable dentro de la franja, sin contadores; "Todas" primero.
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      clipBehavior: Clip.none,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: Esp.x2,
        children: [
          AccesoCategoria(
            nombre: 'Todas',
            cantidad: total,
            // Rejilla 2×2 de cuadros redondeados, como el icono de la web.
            icono: Icons.grid_view_rounded,
            activa: seleccionada == null,
            alTocar: () => alSeleccionar(null),
          ),
          for (final c in categorias)
            AccesoCategoria(
              nombre: c.nombre,
              cantidad: c.cantidad,
              icono: iconoCategoria(c.nombre),
              activa: seleccionada == c.nombre,
              alTocar: () => alSeleccionar(c.nombre),
            ),
        ],
      ),
    );
  }
}

/// Acceso rápido de una categoría (con el color de la categoría).
class AccesoCategoria extends StatelessWidget {
  const AccesoCategoria({
    super.key,
    required this.nombre,
    required this.cantidad,
    required this.icono,
    required this.activa,
    required this.alTocar,
  });

  final String nombre;
  final int cantidad;
  final IconData icono;
  final bool activa;
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    final servicios = cantidad == 1 ? 'servicio' : 'servicios';
    return AccesoCircular(
      etiqueta: nombre,
      icono: icono,
      activo: activa,
      alTocar: alTocar,
      semantica: '$nombre, $cantidad $servicios',
    );
  }
}

/// Chip de filtro sobre la franja negra (historial): activo con el color de
/// marca invertido; inactivo con fondo de campo oscuro y borde.
class ChipFiltro extends StatelessWidget {
  const ChipFiltro({
    super.key,
    required this.nombre,
    required this.activa,
    required this.alTocar,
  });

  final String nombre;
  final bool activa;
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    final forma = BorderRadius.circular(Radios.campo);
    return Semantics(
      button: true,
      selected: activa,
      label: nombre,
      child: ExcludeSemantics(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: Esp.x1),
          child: Material(
            color: activa ? colores.marca : colores.superficie,
            shape: RoundedRectangleBorder(
              borderRadius: forma,
              side: BorderSide(
                color: activa ? colores.marca : colores.lineaFuerte,
              ),
            ),
            child: InkWell(
              onTap: alTocar,
              customBorder: RoundedRectangleBorder(borderRadius: forma),
              focusColor: colores.lineaFuerte,
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 40),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: Esp.x4),
                  child: Center(
                    widthFactor: 1,
                    child: Text(
                      nombre,
                      style: estiloTexto(
                        tamano: Escala.s14,
                        peso: FontWeight.w600,
                        color: activa ? colores.sobreMarca : colores.texto,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Buscador sobre la franja negra, con botón para limpiar. El texto vive en
/// el estado del catálogo.
class BuscadorCatalogo extends StatefulWidget {
  const BuscadorCatalogo({
    super.key,
    required this.texto,
    required this.alCambiar,
    this.habilitado = true,
  });

  final String texto;
  final ValueChanged<String> alCambiar;
  final bool habilitado;

  @override
  State<BuscadorCatalogo> createState() => _BuscadorCatalogoState();
}

class _BuscadorCatalogoState extends State<BuscadorCatalogo> {
  late final TextEditingController _controlador;

  @override
  void initState() {
    super.initState();
    _controlador = TextEditingController(text: widget.texto);
  }

  @override
  void didUpdateWidget(covariant BuscadorCatalogo oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.texto != _controlador.text) {
      _controlador.value = TextEditingValue(
        text: widget.texto,
        selection: TextSelection.collapsed(offset: widget.texto.length),
      );
    }
  }

  @override
  void dispose() {
    _controlador.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colores = ColoresApp.de(context);
    UnderlineInputBorder borde(Color color, double ancho) =>
        UnderlineInputBorder(
          borderRadius: const BorderRadius.vertical(
            top: Radius.circular(Radios.campo),
          ),
          borderSide: BorderSide(color: color, width: ancho),
        );
    return TextField(
      controller: _controlador,
      enabled: widget.habilitado,
      onChanged: widget.alCambiar,
      inputFormatters: Entrada.busqueda.formatos(),
      textInputAction: TextInputAction.search,
      cursorColor: colores.texto,
      style: estiloTexto(
        tamano: Escala.s16,
        peso: FontWeight.w500,
        color: colores.texto,
      ),
      decoration: InputDecoration(
        // Igual que la web: el texto guía nombra el campo y el lector de
        // pantalla lo anuncia como su etiqueta.
        hintText: 'Buscar servicio o producto',
        filled: true,
        fillColor: colores.superficie,
        hintStyle: estiloTexto(
          tamano: Escala.s16,
          color: colores.textoSecundario,
        ),
        prefixIcon: Icon(
          Icons.search,
          color: colores.textoSecundario,
        ),
        contentPadding: const EdgeInsets.symmetric(vertical: Esp.x4),
        border: borde(colores.textoSecundario, 1),
        enabledBorder: borde(colores.textoSecundario, 1),
        disabledBorder: borde(colores.lineaFuerte, 1),
        focusedBorder: borde(colores.texto, 2),
        suffixIcon: widget.texto.isEmpty
            ? null
            : IconButton(
                icon: Icon(Icons.close, color: colores.texto),
                tooltip: 'Limpiar búsqueda',
                onPressed: () => widget.alCambiar(''),
              ),
      ),
    );
  }
}
