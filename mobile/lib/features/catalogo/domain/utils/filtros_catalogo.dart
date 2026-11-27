import '../entities/producto.dart';
import 'formato_catalogo.dart';

/// Categoría con el número de productos que contiene.
class CategoriaConteo {
  const CategoriaConteo(this.nombre, this.cantidad);

  final String nombre;
  final int cantidad;

  @override
  bool operator ==(Object other) =>
      other is CategoriaConteo &&
      other.nombre == nombre &&
      other.cantidad == cantidad;

  @override
  int get hashCode => Object.hash(nombre, cantidad);
}

/// Categorías presentes en los datos reales, ordenadas por nombre.
List<CategoriaConteo> categoriasDe(List<Producto> productos) {
  final conteo = <String, int>{};
  for (final p in productos) {
    conteo[p.categoriaNombre] = (conteo[p.categoriaNombre] ?? 0) + 1;
  }
  final lista =
      conteo.entries.map((e) => CategoriaConteo(e.key, e.value)).toList();
  lista.sort(
    (a, b) => normalizarTexto(a.nombre).compareTo(normalizarTexto(b.nombre)),
  );
  return lista;
}

/// Filtra por categoría exacta y por texto (en `servicio` y `producto`,
/// sin distinguir mayúsculas ni acentos). Ambos filtros se combinan.
List<Producto> filtrarProductos(
  List<Producto> productos, {
  String? categoria,
  String busqueda = '',
}) {
  final consulta = normalizarTexto(busqueda.trim());
  return productos.where((p) {
    if (categoria != null && p.categoriaNombre != categoria) return false;
    if (consulta.isEmpty) return true;
    return normalizarTexto(p.servicio).contains(consulta) ||
        normalizarTexto(p.producto).contains(consulta);
  }).toList();
}
