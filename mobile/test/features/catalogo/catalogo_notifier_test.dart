import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/entities/producto.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/repositories/catalogo_repository.dart';
import 'package:gesto_pago_mobile/features/catalogo/domain/usecases/obtener_catalogo_usecase.dart';
import 'package:gesto_pago_mobile/features/catalogo/presentation/providers/catalogo_providers.dart';

class _RepoFalso implements CatalogoRepository {
  _RepoFalso(this.resultado);

  Result<List<Producto>> resultado;

  @override
  Future<Result<List<Producto>>> obtenerProductos() async => resultado;
}

Failure _fallo(TipoFallo tipo) => Failure(
      tipo: tipo,
      mensaje: 'detalle tecnico del backend: token 500 API',
      httpStatus: 500,
    );

const _datos = <Producto>[
  Producto(servicio: 'CFE', categoria: 'Luz', idServicio: 1, idProducto: 1),
  Producto(servicio: 'Telcel', categoria: 'Recargas', idServicio: 2, idProducto: 2),
];

Future<CatalogoNotifier> _crear(Result<List<Producto>> r) async {
  final n = CatalogoNotifier(ObtenerCatalogoUseCase(_RepoFalso(r)));
  await n.cargar();
  return n;
}

void main() {
  test('exito deja el estado listo con categorias', () async {
    final n = await _crear(const Ok<List<Producto>>(_datos));
    expect(n.state.estado, EstadoCatalogo.listo);
    expect(n.state.categorias.length, 2);
    expect(n.state.filtrados.length, 2);
  });

  test('404 se trata como vacio, no como error', () async {
    final n = await _crear(_fallo(TipoFallo.noEncontrado));
    expect(n.state.estado, EstadoCatalogo.vacio);
    expect(n.state.mensajeError, isNull);
  });

  test('lista vacia es estado vacio', () async {
    final n = await _crear(const Ok<List<Producto>>(<Producto>[]));
    expect(n.state.estado, EstadoCatalogo.vacio);
  });

  test('error muestra solo mensaje amigable', () async {
    final n = await _crear(_fallo(TipoFallo.servidor));
    expect(n.state.estado, EstadoCatalogo.error);
    final m = n.state.mensajeError!.toLowerCase();
    expect(m, isNot(contains('detalle tecnico')));
    expect(m, isNot(contains('500')));
    expect(m, isNot(contains('servidor')));
    expect(m, isNot(contains('api')));
    expect(m, isNot(contains('token')));
  });

  test('error de red tambien es amigable', () async {
    final n = await _crear(_fallo(TipoFallo.red));
    expect(n.state.estado, EstadoCatalogo.error);
    expect(n.state.mensajeError, isNotEmpty);
  });

  test('filtros se combinan y se quitan', () async {
    final n = await _crear(const Ok<List<Producto>>(_datos));
    n.seleccionarCategoria('Luz');
    expect(n.state.filtrados.length, 1);
    n.buscar('telcel');
    expect(n.state.filtrados, isEmpty);
    expect(n.state.hayFiltros, isTrue);
    n.quitarFiltros();
    expect(n.state.filtrados.length, 2);
    expect(n.state.categoriaSeleccionada, isNull);
    expect(n.state.busqueda, '');
  });
}
