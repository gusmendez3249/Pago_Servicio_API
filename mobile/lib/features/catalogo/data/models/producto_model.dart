import '../../../../core/http/json_helpers.dart';
import '../../domain/entities/producto.dart';

class ProductoModel {
  const ProductoModel(this.entidad);

  final Producto entidad;

  /// Tolerante a nulos, campos ausentes y tipos numéricos como texto.
  factory ProductoModel.fromJson(Map<String, dynamic> json) {
    return ProductoModel(
      Producto(
        servicio: aString(json['servicio']),
        producto: aString(json['producto']),
        idServicio: aInt(json['idServicio']),
        idProducto: aInt(json['idProducto']),
        idCatTipoServicio: aInt(json['idCatTipoServicio']),
        tipoFront: aInt(json['tipoFront']),
        hasDigitoVerificador: aBool(json['hasDigitoVerificador']),
        precio: aDouble(json['precio']),
        showAyuda: aBool(json['showAyuda']),
        tipoReferencia: aString(json['tipoReferencia']),
        legend: aString(json['legend']),
        logoUrl: aString(json['logoUrl']),
        ayudaUrl: aString(json['ayudaUrl']),
        categoria: aString(json['categoria']),
      ),
    );
  }

  Map<String, dynamic> toJson() => {
        'servicio': entidad.servicio,
        'producto': entidad.producto,
        'idServicio': entidad.idServicio,
        'idProducto': entidad.idProducto,
        'idCatTipoServicio': entidad.idCatTipoServicio,
        'tipoFront': entidad.tipoFront,
        'hasDigitoVerificador': entidad.hasDigitoVerificador,
        'precio': entidad.precio,
        'showAyuda': entidad.showAyuda,
        'tipoReferencia': entidad.tipoReferencia,
        'legend': entidad.legend,
        'logoUrl': entidad.logoUrl,
        'ayudaUrl': entidad.ayudaUrl,
        'categoria': entidad.categoria,
      };
}
