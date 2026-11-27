import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/entities/producto.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/utils/filtros_catalogo.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/utils/formato_catalogo.dart';

const _productos = <Producto>[
  Producto(
    servicio: 'CFE',
    producto: 'Recibo de luz',
    categoria: 'Pago de servicios',
    idServicio: 1,
    idProducto: 1,
  ),
  Producto(
    servicio: 'Telcel',
    producto: 'Recarga',
    categoria: 'Recargas',
    idServicio: 2,
    idProducto: 2,
  ),
  Producto(
    servicio: 'Teléfonos de México',
    producto: 'Pago de línea',
    categoria: 'Pago de servicios',
    idServicio: 3,
    idProducto: 3,
  ),
  Producto(servicio: 'Sin categoría', idServicio: 4, idProducto: 4),
];

void main() {
  group('formatearMoneda', () {
    test('agrupa miles y dos decimales', () {
      expect(formatearMoneda(1234.5), '\$1,234.50');
      expect(formatearMoneda(0), '\$0.00');
      expect(formatearMoneda(12), '\$12.00');
      expect(formatearMoneda(1234567.891), '\$1,234,567.89');
      expect(formatearMoneda(999.999), '\$1,000.00');
    });
  });

  group('iniciales', () {
    test('una, dos o mas palabras y vacio', () {
      expect(iniciales('Telmex'), 'TE');
      expect(iniciales('Luz CFE'), 'LC');
      expect(iniciales('agua de la ciudad'), 'AD');
      expect(iniciales('  '), '?');
      expect(iniciales(null), '?');
    });
  });

  group('normalizarTexto', () {
    test('quita acentos y mayusculas', () {
      expect(normalizarTexto('TELÉFONOS Ñandú'), 'telefonos nandu');
    });
  });

  group('categoriasDe', () {
    test('deriva de los datos, ordenadas y con conteo', () {
      final c = categoriasDe(_productos);
      expect(c.map((e) => e.nombre).toList(), [
        'Otros',
        'Pago de servicios',
        'Recargas',
      ]);
      expect(c[1].cantidad, 2);
    });
  });

  group('filtrarProductos', () {
    test('sin filtros devuelve todo', () {
      expect(filtrarProductos(_productos).length, 4);
    });

    test('por categoria', () {
      final r = filtrarProductos(_productos, categoria: 'Recargas');
      expect(r.map((p) => p.servicio), ['Telcel']);
    });

    test('busqueda sin acentos ni mayusculas en servicio y producto', () {
      expect(
        filtrarProductos(_productos, busqueda: 'telefonos')
            .map((p) => p.servicio),
        ['Teléfonos de México'],
      );
      expect(
        filtrarProductos(_productos, busqueda: 'LINEA').length,
        1,
      );
      expect(filtrarProductos(_productos, busqueda: 'recibo').length, 1);
    });

    test('combina categoria y busqueda', () {
      final r = filtrarProductos(
        _productos,
        categoria: 'Pago de servicios',
        busqueda: 'tel',
      );
      expect(r.length, 1);
      expect(r.first.servicio, 'Teléfonos de México');
      expect(
        filtrarProductos(_productos, categoria: 'Recargas', busqueda: 'cfe'),
        isEmpty,
      );
    });
  });
}
