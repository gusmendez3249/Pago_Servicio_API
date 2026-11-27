import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/features/catalogo/data/models/producto_model.dart';

void main() {
  test('fromJson tolera todos los campos nulos o ausentes', () {
    final p = ProductoModel.fromJson(<String, dynamic>{
      'servicio': null,
      'precio': null,
    }).entidad;
    expect(p.servicio, isNull);
    expect(p.producto, isNull);
    expect(p.idServicio, isNull);
    expect(p.precio, isNull);
    expect(p.tipoFront, isNull);
    expect(p.hasDigitoVerificador, isNull);
    expect(p.legend, isNull);
    expect(p.logoUrl, isNull);
    expect(p.ayudaUrl, isNull);
    expect(p.categoria, isNull);
    expect(p.categoriaNombre, 'Otros');
  });

  test('fromJson convierte tipos, numeros como texto y campos nuevos', () {
    final p = ProductoModel.fromJson(<String, dynamic>{
      'servicio': 'Luz',
      'producto': 'Recibo',
      'idServicio': 7,
      'idProducto': '12',
      'idCatTipoServicio': 2,
      'tipoFront': 2,
      'hasDigitoVerificador': true,
      'precio': 150,
      'showAyuda': false,
      'tipoReferencia': 'R',
      'legend': 'Linea 1\nLinea 2',
      'logoUrl': 'https://x.test/logo.png',
      'ayudaUrl': 'https://x.test/ayuda.png',
      'categoria': 'Pago de servicios',
    }).entidad;
    expect(p.idProducto, 12);
    expect(p.precio, 150.0);
    expect(p.tipoFront, 2);
    expect(p.hasDigitoVerificador, isTrue);
    expect(p.showAyuda, isFalse);
    expect(p.servicio, 'Luz');
    expect(p.legend, contains('\n'));
    expect(p.logoUrl, 'https://x.test/logo.png');
    expect(p.ayudaUrl, 'https://x.test/ayuda.png');
    expect(p.categoria, 'Pago de servicios');
  });

  test('regla Comision/Precio segun tipoFront', () {
    final comision =
        ProductoModel.fromJson(<String, dynamic>{'tipoFront': 2}).entidad;
    final precio =
        ProductoModel.fromJson(<String, dynamic>{'tipoFront': 1}).entidad;
    final sin = ProductoModel.fromJson(<String, dynamic>{}).entidad;
    expect(comision.esComision, isTrue);
    expect(comision.etiquetaImporte, 'Comisión');
    expect(precio.esComision, isFalse);
    expect(precio.etiquetaImporte, 'Precio');
    expect(sin.etiquetaImporte, 'Precio');
    final envio =
        ProductoModel.fromJson(<String, dynamic>{'tipoFront': 40}).entidad;
    expect(envio.esComision, isTrue);
    expect(envio.etiquetaImporte, 'Comisión');
  });
}
