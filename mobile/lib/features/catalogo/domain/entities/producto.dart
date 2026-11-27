/// Texto de la categoría cuando el producto no trae una.
const String kCategoriaPorDefecto = 'Otros';

/// Valor de `tipoFront` que indica que el `precio` es en realidad la comisión.
const int kTipoFrontComision = 2;

/// `tipoFront` 40 (envíos de dinero): el `precio` del catálogo también es la comisión.
const int kTipoFrontEnvioDinero = 40;

/// `tipoFront` cuyo `precio` se muestra como comisión.
const Set<int> kTiposFrontComision = {kTipoFrontComision, kTipoFrontEnvioDinero};

/// Producto/servicio del catálogo. Cualquier campo puede venir nulo.
class Producto {
  const Producto({
    this.servicio,
    this.producto,
    this.idServicio,
    this.idProducto,
    this.idCatTipoServicio,
    this.tipoFront,
    this.hasDigitoVerificador,
    this.precio,
    this.showAyuda,
    this.tipoReferencia,
    this.legend,
    this.logoUrl,
    this.ayudaUrl,
    this.categoria,
  });

  final String? servicio;
  final String? producto;
  final int? idServicio;
  final int? idProducto;
  final int? idCatTipoServicio;
  final int? tipoFront;
  final bool? hasDigitoVerificador;
  final double? precio;
  final bool? showAyuda;
  final String? tipoReferencia;
  final String? legend;
  final String? logoUrl;
  final String? ayudaUrl;
  final String? categoria;

  /// Si es true, [precio] es la comisión del servicio.
  bool get esComision => kTiposFrontComision.contains(tipoFront);

  /// Etiqueta del importe según la regla de negocio.
  String get etiquetaImporte => esComision ? 'Comisión' : 'Precio';

  /// Nombre de categoría listo para mostrar (nunca vacío).
  String get categoriaNombre {
    final c = categoria?.trim() ?? '';
    return c.isEmpty ? kCategoriaPorDefecto : c;
  }
}
