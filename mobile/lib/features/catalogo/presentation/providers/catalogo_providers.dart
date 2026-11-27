import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/error/mensajes_error.dart';
import '../../../../core/http/api_providers.dart';
import '../../../../core/http/result.dart';
import '../../data/datasources/catalogo_remote_datasource.dart';
import '../../data/repositories/catalogo_repository_impl.dart';
import '../../domain/entities/producto.dart';
import '../../domain/repositories/catalogo_repository.dart';
import '../../domain/usecases/obtener_catalogo_usecase.dart';
import '../../domain/utils/filtros_catalogo.dart';

enum EstadoCatalogo { cargando, listo, vacio, error }

class CatalogoState {
  const CatalogoState({
    this.estado = EstadoCatalogo.cargando,
    this.productos = const <Producto>[],
    this.categoriaSeleccionada,
    this.busqueda = '',
    this.mensajeError,
  });

  final EstadoCatalogo estado;

  /// Lista completa recibida (sin filtrar).
  final List<Producto> productos;

  /// Nombre de la categoría elegida; null equivale a "Todas".
  final String? categoriaSeleccionada;
  final String busqueda;

  /// Mensaje amigable (nunca texto técnico) cuando [estado] es error.
  final String? mensajeError;

  /// Categorías derivadas de los datos reales, con conteo.
  List<CategoriaConteo> get categorias => categoriasDe(productos);

  /// Lista tras aplicar categoría y búsqueda.
  List<Producto> get filtrados => filtrarProductos(
        productos,
        categoria: categoriaSeleccionada,
        busqueda: busqueda,
      );

  bool get hayFiltros =>
      categoriaSeleccionada != null || busqueda.trim().isNotEmpty;

  CatalogoState copiar({
    EstadoCatalogo? estado,
    List<Producto>? productos,
    String? categoriaSeleccionada,
    bool limpiarCategoria = false,
    String? busqueda,
    String? mensajeError,
    bool limpiarError = false,
  }) {
    return CatalogoState(
      estado: estado ?? this.estado,
      productos: productos ?? this.productos,
      categoriaSeleccionada: limpiarCategoria
          ? null
          : (categoriaSeleccionada ?? this.categoriaSeleccionada),
      busqueda: busqueda ?? this.busqueda,
      mensajeError: limpiarError ? null : (mensajeError ?? this.mensajeError),
    );
  }
}

class CatalogoNotifier extends StateNotifier<CatalogoState> {
  CatalogoNotifier(this._obtenerCatalogo) : super(const CatalogoState()) {
    cargar();
  }

  final ObtenerCatalogoUseCase _obtenerCatalogo;

  /// Carga el catálogo. Con [mostrarCarga] false (pull-to-refresh) mantiene la
  /// lista visible mientras se actualiza.
  Future<void> cargar({bool mostrarCarga = true}) async {
    if (mostrarCarga) {
      state = state.copiar(
        estado: EstadoCatalogo.cargando,
        limpiarError: true,
      );
    }
    Result<List<Producto>> resultado;
    try {
      resultado = await _obtenerCatalogo();
    } catch (_) {
      resultado = const Failure(
        tipo: TipoFallo.desconocido,
        mensaje: '',
      );
    }
    if (!mounted) return;
    if (resultado is Ok<List<Producto>>) {
      final lista = resultado.data;
      state = state.copiar(
        estado: lista.isEmpty ? EstadoCatalogo.vacio : EstadoCatalogo.listo,
        productos: lista,
        limpiarError: true,
      );
    } else if (resultado is Failure) {
      state = state.copiar(
        estado: EstadoCatalogo.error,
        mensajeError: mensajeDeFallo(resultado),
      );
    }
  }

  void seleccionarCategoria(String? categoria) {
    state = categoria == null
        ? state.copiar(limpiarCategoria: true)
        : state.copiar(categoriaSeleccionada: categoria);
  }

  void buscar(String texto) {
    state = state.copiar(busqueda: texto);
  }

  void quitarFiltros() {
    state = state.copiar(limpiarCategoria: true, busqueda: '');
  }
}

final catalogoRepositoryProvider = Provider<CatalogoRepository>(
  (ref) => CatalogoRepositoryImpl(
    CatalogoRemoteDataSource(ref.watch(apiClientProvider)),
  ),
);

final obtenerCatalogoUseCaseProvider = Provider<ObtenerCatalogoUseCase>(
  (ref) => ObtenerCatalogoUseCase(ref.watch(catalogoRepositoryProvider)),
);

final catalogoProvider = StateNotifierProvider<CatalogoNotifier, CatalogoState>(
  (ref) => CatalogoNotifier(ref.watch(obtenerCatalogoUseCaseProvider)),
);
