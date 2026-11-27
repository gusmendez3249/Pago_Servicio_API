import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/http/result.dart';
import '../../domain/entities/elemento_historial.dart';
import '../../domain/usecases/obtener_historial_usecase.dart';
import '../../domain/utils/reglas_historial.dart';

/// Tamaño de página que se pide al servidor.
const int kTamanioPaginaHistorial = 20;

enum EstadoHistorial { cargando, listo, vacio, error }

class HistorialState {
  const HistorialState({
    this.estado = EstadoHistorial.cargando,
    this.filtro = FiltroHistorial.todos,
    this.elementos = const <ElementoHistorial>[],
    this.siguientePagina = 0,
    this.cursores = const <String, int>{},
    this.masPorEstatus = const <String, bool>{},
    this.hayMas = false,
    this.cargandoMas = false,
    this.errorMas = false,
  });

  final EstadoHistorial estado;
  final FiltroHistorial filtro;
  final List<ElementoHistorial> elementos;

  /// Número de la próxima página que se pedirá al servidor.
  final int siguientePagina;

  /// Próxima página por consulta de estatus ('' = sin filtro).
  final Map<String, int> cursores;

  /// Si cada consulta de estatus tiene más páginas.
  final Map<String, bool> masPorEstatus;
  final bool hayMas;
  final bool cargandoMas;

  /// Falló "Ver más": la lista actual se conserva.
  final bool errorMas;

  HistorialState copiar({
    EstadoHistorial? estado,
    FiltroHistorial? filtro,
    List<ElementoHistorial>? elementos,
    int? siguientePagina,
    Map<String, int>? cursores,
    Map<String, bool>? masPorEstatus,
    bool? hayMas,
    bool? cargandoMas,
    bool? errorMas,
  }) {
    return HistorialState(
      estado: estado ?? this.estado,
      filtro: filtro ?? this.filtro,
      elementos: elementos ?? this.elementos,
      siguientePagina: siguientePagina ?? this.siguientePagina,
      cursores: cursores ?? this.cursores,
      masPorEstatus: masPorEstatus ?? this.masPorEstatus,
      hayMas: hayMas ?? this.hayMas,
      cargandoMas: cargandoMas ?? this.cargandoMas,
      errorMas: errorMas ?? this.errorMas,
    );
  }
}

/// Estado del historial en memoria (nunca se guarda en almacenamiento).
class HistorialNotifier extends StateNotifier<HistorialState> {
  HistorialNotifier(this._obtener, {bool cargarAlCrear = true})
      : super(const HistorialState()) {
    if (cargarAlCrear) cargar();
  }

  final ObtenerHistorialUseCase _obtener;

  /// Cada solicitud nueva (o la limpieza) invalida las respuestas en vuelo.
  int _generacion = 0;

  /// Carga desde la primera página. Con [mostrarCarga] false (pull-to-refresh)
  /// mantiene la lista visible mientras se actualiza.
  Future<void> cargar({bool mostrarCarga = true}) {
    if (mostrarCarga) {
      state = state.copiar(
        estado: EstadoHistorial.cargando,
        elementos: const <ElementoHistorial>[],
        siguientePagina: 0,
        cursores: const <String, int>{},
        masPorEstatus: const <String, bool>{},
        hayMas: false,
        cargandoMas: false,
        errorMas: false,
      );
    }
    return _pedir(reiniciar: true);
  }

  /// Pull-to-refresh.
  Future<void> actualizar() => cargar(mostrarCarga: false);

  /// "Ver más": pide la siguiente página y la acumula.
  Future<void> verMas() {
    if (!state.hayMas ||
        state.cargandoMas ||
        state.estado != EstadoHistorial.listo) {
      return Future<void>.value();
    }
    state = state.copiar(cargandoMas: true, errorMas: false);
    return _pedir(reiniciar: false);
  }

  /// Cambiar de pestaña reinicia la paginación.
  Future<void> cambiarFiltro(FiltroHistorial filtro) {
    if (filtro == state.filtro) return Future<void>.value();
    state = HistorialState(filtro: filtro);
    return _pedir(reiniciar: true);
  }

  /// Cierre de sesión o 401: se descarta todo.
  void limpiar() {
    _generacion++;
    state = const HistorialState();
  }

  Future<void> _pedir({required bool reiniciar}) async {
    final generacion = ++_generacion;
    final filtro = state.filtro;
    final claves = filtro.estatusesServidor;
    final cursores =
        reiniciar ? <String, int>{} : Map<String, int>.of(state.cursores);
    final mas = reiniciar
        ? <String, bool>{}
        : Map<String, bool>.of(state.masPorEstatus);
    var acumulado = reiniciar
        ? <ElementoHistorial>[]
        : List<ElementoHistorial>.of(state.elementos);

    // Cada estatus se pide por separado, con su propia paginación.
    final activas = [
      for (final c in claves)
        if (reiniciar || (mas[c ?? ''] ?? false)) c,
    ];
    final resultados = await Future.wait(
      activas.map((c) => _pedirPagina(c, cursores[c ?? ''] ?? 0)),
    );
    if (!mounted || generacion != _generacion) return;

    if (resultados.any((r) => r is! Ok<PaginaHistorial>)) {
      state = reiniciar
          ? state.copiar(
              estado: EstadoHistorial.error,
              cargandoMas: false,
              errorMas: false,
            )
          : state.copiar(cargandoMas: false, errorMas: true);
      return;
    }
    for (var i = 0; i < activas.length; i++) {
      final datos = (resultados[i] as Ok<PaginaHistorial>).data;
      final clave = activas[i] ?? '';
      acumulado.addAll(datos.elementos);
      cursores[clave] = (cursores[clave] ?? 0) + 1;
      mas[clave] = datos.hayMas;
    }
    if (claves.length > 1) acumulado = _combinar(acumulado);

    state = state.copiar(
      estado: acumulado.isEmpty ? EstadoHistorial.vacio : EstadoHistorial.listo,
      elementos: acumulado,
      siguientePagina: cursores[claves.first ?? ''] ?? 0,
      cursores: cursores,
      masPorEstatus: mas,
      hayMas: mas.values.any((v) => v),
      cargandoMas: false,
      errorMas: false,
    );
  }

  Future<Result<PaginaHistorial>> _pedirPagina(
      String? estatus, int pagina) async {
    try {
      return await _obtener(
        estatus: estatus,
        pagina: pagina,
        tamanio: kTamanioPaginaHistorial,
      );
    } catch (_) {
      return const Failure(tipo: TipoFallo.desconocido, mensaje: '');
    }
  }

  /// Fecha descendente (sin fecha al final) y sin repetidos por `upc`.
  List<ElementoHistorial> _combinar(List<ElementoHistorial> lista) {
    final vistos = <String>{};
    final unicos = <ElementoHistorial>[
      for (final e in lista)
        if (e.upc == null || vistos.add(e.upc!)) e,
    ];
    DateTime? fecha(ElementoHistorial e) =>
        DateTime.tryParse((e.fechaTransaccion ?? '').trim());
    final indexados = unicos.asMap().entries.toList()
      ..sort((a, b) {
        final fa = fecha(a.value);
        final fb = fecha(b.value);
        if (fa == null && fb == null) return a.key.compareTo(b.key);
        if (fa == null) return 1;
        if (fb == null) return -1;
        final c = fb.compareTo(fa);
        return c != 0 ? c : a.key.compareTo(b.key);
      });
    return [for (final e in indexados) e.value];
  }
}
