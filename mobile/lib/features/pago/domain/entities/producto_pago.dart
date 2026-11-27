/// Datos del producto que se paga (tomados del catálogo).
class ProductoPago {
  const ProductoPago({
    required this.idServicio,
    required this.idProducto,
    required this.tipoFront,
    required this.servicio,
    required this.producto,
    this.precio,
    this.logoUrl,
  });

  final int idServicio;
  final int idProducto;
  final int? tipoFront;
  final String servicio;
  final String producto;
  final double? precio;
  final String? logoUrl;
}
