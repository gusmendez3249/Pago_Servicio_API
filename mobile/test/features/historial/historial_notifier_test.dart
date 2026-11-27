import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/features/historial/domain/entities/elemento_historial.dart';
import 'package:gesto_pago_mobile/features/historial/domain/repositories/historial_repository.dart';
import 'package:gesto_pago_mobile/features/historial/domain/usecases/obtener_historial_usecase.dart';
import 'package:gesto_pago_mobile/features/historial/domain/utils/reglas_historial.dart';
import 'package:gesto_pago_mobile/features/historial/presentation/providers/historial_notifier.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/comprobante_pago.dart';

class RepoHistorialFalso implements HistorialRepository {
  final List<({String? estatus, int pagina, int tamanio})> llamadas = [];

  /// Responde según la página pedida; null => fallo.
  Result<PaginaHistorial> Function(String? estatus, int pagina) responder =
      (_, __) => const Ok(
            PaginaHistorial(
              elementos: [],
              pagina: 0,
              tamanio: 20,
              total: 0,
              hayMas: false,
            ),
          );

  @override
  Future<Result<PaginaHistorial>> obtener({
    String? estatus,
    required int pagina,
    required int tamanio,
  }) async {
    llamadas.add((estatus: estatus, pagina: pagina, tamanio: tamanio));
    return responder(estatus, pagina);
  }
}

ElementoHistorial el(String upc, [EstatusPago e = EstatusPago.confirmada]) =>
    ElementoHistorial(estatus: e, upc: upc, monto: 10);

PaginaHistorial pag(List<ElementoHistorial> l, int n, {bool hayMas = false}) =>
    PaginaHistorial(
      elementos: l,
      pagina: n,
      tamanio: 20,
      total: 100,
      hayMas: hayMas,
    );

const _fallo = Failure(
  tipo: TipoFallo.servidor,
  mensaje: 'detalle tecnico',
  httpStatus: 500,
);

Future<HistorialNotifier> crear(RepoHistorialFalso repo) async {
  final n = HistorialNotifier(ObtenerHistorialUseCase(repo));
  await Future<void>.delayed(Duration.zero);
  return n;
}

void main() {
  test('carga la primera página y queda listo', () async {
    final repo = RepoHistorialFalso()
      ..responder = (_, p) => Ok(pag([el('A'), el('B')], p, hayMas: true));
    final n = await crear(repo);
    expect(n.state.estado, EstadoHistorial.listo);
    expect(n.state.elementos.map((e) => e.upc), ['A', 'B']);
    expect(n.state.hayMas, isTrue);
    expect(repo.llamadas.single, (estatus: null, pagina: 0, tamanio: 20));
  });

  test('sin resultados es vacío', () async {
    final n = await crear(RepoHistorialFalso());
    expect(n.state.estado, EstadoHistorial.vacio);
  });

  test('error y reintento', () async {
    final repo = RepoHistorialFalso()..responder = (_, __) => _fallo;
    final n = await crear(repo);
    expect(n.state.estado, EstadoHistorial.error);
    repo.responder = (_, p) => Ok(pag([el('A')], p));
    await n.cargar();
    expect(n.state.estado, EstadoHistorial.listo);
    expect(n.state.elementos, hasLength(1));
  });

  test('"Ver más" acumula y respeta hayMas', () async {
    final repo = RepoHistorialFalso()
      ..responder = (_, p) => p == 0
          ? Ok(pag([el('A')], 0, hayMas: true))
          : Ok(pag([el('B')], 1));
    final n = await crear(repo);
    await n.verMas();
    expect(n.state.elementos.map((e) => e.upc), ['A', 'B']);
    expect(n.state.hayMas, isFalse);
    expect(repo.llamadas.last.pagina, 1);
    final antes = repo.llamadas.length;
    await n.verMas();
    expect(repo.llamadas.length, antes);
  });

  test('un fallo en "Ver más" conserva la lista y permite reintentar', () async {
    final repo = RepoHistorialFalso()
      ..responder = (_, p) => Ok(pag([el('A')], p, hayMas: true));
    final n = await crear(repo);
    repo.responder = (_, __) => _fallo;
    await n.verMas();
    expect(n.state.estado, EstadoHistorial.listo);
    expect(n.state.errorMas, isTrue);
    expect(n.state.elementos, hasLength(1));
    repo.responder = (_, p) => Ok(pag([el('B')], p));
    await n.verMas();
    expect(n.state.errorMas, isFalse);
    expect(n.state.elementos, hasLength(2));
  });

  test('cambiar de filtro reinicia la página y pide el estatus', () async {
    final repo = RepoHistorialFalso()
      ..responder = (_, p) => Ok(pag([el('A')], p, hayMas: true));
    final n = await crear(repo);
    await n.verMas();
    await n.cambiarFiltro(FiltroHistorial.exitosos);
    expect(repo.llamadas.last, (estatus: 'CONFIRMADA', pagina: 0, tamanio: 20));
    expect(n.state.elementos, hasLength(1));
    expect(n.state.siguientePagina, 1);
    await n.cambiarFiltro(FiltroHistorial.rechazados);
    expect(repo.llamadas.last.estatus, 'RECHAZADA');
  });

  test('pendientes pide PENDIENTE e INDETERMINADA por separado, combina '
      'por fecha desc y descarta repetidos', () async {
    ElementoHistorial f(String upc, String fecha, EstatusPago e) =>
        ElementoHistorial(estatus: e, upc: upc, fechaTransaccion: fecha);
    final repo = RepoHistorialFalso()
      ..responder = (est, p) {
        if (est == 'PENDIENTE') {
          return Ok(pag([
            f('A', '2026-11-10T10:00:00', EstatusPago.pendiente),
            f('D', '2026-11-12T10:00:00', EstatusPago.pendiente),
          ], p, hayMas: p == 0));
        }
        return Ok(pag([
          f('B', '2026-11-11T10:00:00', EstatusPago.indeterminada),
          f('A', '2026-11-10T10:00:00', EstatusPago.pendiente),
        ], p));
      };
    final n = await crear(repo);
    await n.cambiarFiltro(FiltroHistorial.pendientes);
    expect(
      repo.llamadas.map((l) => l.estatus).toSet(),
      {null, 'PENDIENTE', 'INDETERMINADA'},
    );
    expect(n.state.elementos.map((e) => e.upc), ['D', 'B', 'A']);
    expect(n.state.hayMas, isTrue);

    repo.llamadas.clear();
    await n.verMas();
    // Solo la consulta que aún tiene páginas, con su propio cursor.
    expect(repo.llamadas.map((l) => (l.estatus, l.pagina)), [('PENDIENTE', 1)]);
    expect(n.state.hayMas, isFalse);
  });

  test('limpiar descarta el estado y las respuestas en vuelo', () async {
    final repo = RepoHistorialFalso()
      ..responder = (_, p) => Ok(pag([el('A')], p, hayMas: true));
    final n = await crear(repo);
    n.limpiar();
    expect(n.state.elementos, isEmpty);
    expect(n.state.hayMas, isFalse);
    expect(n.state.filtro, FiltroHistorial.todos);
    final f = n.cargar();
    n.limpiar();
    await f;
    expect(n.state.elementos, isEmpty);
  });
}
