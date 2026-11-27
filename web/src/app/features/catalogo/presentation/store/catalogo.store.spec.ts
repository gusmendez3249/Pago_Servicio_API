import { TestBed } from '@angular/core/testing';
import { Failure, Result } from '../../../../core/http/result';
import { Producto } from '../../domain/models/producto';
import { CatalogoRepository } from '../../domain/repository/catalogo-repository';
import { CatalogoStore } from './catalogo.store';

function prod(p: Partial<Producto>): Producto {
  return {
    servicio: '', producto: '', idServicio: 1, idProducto: 1, idCatTipoServicio: null, tipoFront: null,
    hasDigitoVerificador: false, precio: null, showAyuda: false, tipoReferencia: null, legend: null,
    logoUrl: null, ayudaUrl: null, categoria: null, ...p,
  };
}

const DATOS: Producto[] = [
  prod({ servicio: 'Teléfonos de México', producto: 'Telmex', idProducto: 1, categoria: 'Pago de servicios' }),
  prod({ servicio: 'CFE', producto: 'Luz', idProducto: 2, categoria: 'Pago de servicios' }),
  prod({ servicio: 'Telcel', producto: 'Recarga $50', idProducto: 3, categoria: 'Recargas' }),
  prod({ servicio: 'Sin categoría', producto: 'Algo', idProducto: 4 }),
];

function falla(tipo: Failure['tipo'], httpStatus: number, mensaje = 'detalle tecnico del servidor'): Failure {
  return { ok: false, tipo, httpStatus, codigo: httpStatus, mensaje };
}

function crear(respuesta: Result<Producto[]> | (() => Promise<Result<Producto[]>>)) {
  const obtenerProductos = vi.fn(
    typeof respuesta === 'function' ? respuesta : () => Promise.resolve(respuesta),
  );
  TestBed.configureTestingModule({
    providers: [{ provide: CatalogoRepository, useValue: { obtenerProductos } }],
  });
  return { store: TestBed.inject(CatalogoStore), obtenerProductos };
}

const ok = (data: Producto[]): Result<Producto[]> => ({ ok: true, data, mensaje: '', httpStatus: 200 });

describe('CatalogoStore', () => {
  it('inicia cargando y pasa a listo', async () => {
    const { store } = crear(ok(DATOS));
    expect(store.estado()).toBe('cargando');
    await store.cargar();
    expect(store.estado()).toBe('listo');
    expect(store.filtrados()).toHaveLength(4);
  });

  it('deriva categorías reales, ordenadas y con conteo', async () => {
    const { store } = crear(ok(DATOS));
    await store.cargar();
    expect(store.categorias()).toEqual([
      { nombre: 'Otros', conteo: 1 },
      { nombre: 'Pago de servicios', conteo: 2 },
      { nombre: 'Recargas', conteo: 1 },
    ]);
  });

  it('filtra por categoría', async () => {
    const { store } = crear(ok(DATOS));
    await store.cargar();
    store.seleccionarCategoria('Recargas');
    expect(store.filtrados().map((p) => p.servicio)).toEqual(['Telcel']);
    store.seleccionarCategoria(null);
    expect(store.filtrados()).toHaveLength(4);
  });

  it('busca sin acentos ni mayúsculas en servicio y producto', async () => {
    const { store } = crear(ok(DATOS));
    await store.cargar();
    store.buscar('TELEFONOS');
    expect(store.filtrados().map((p) => p.servicio)).toEqual(['Teléfonos de México']);
    store.buscar('luz');
    expect(store.filtrados().map((p) => p.servicio)).toEqual(['CFE']);
    store.buscar('zzz');
    expect(store.filtrados()).toEqual([]);
  });

  it('combina categoría y búsqueda y permite limpiar', async () => {
    const { store } = crear(ok(DATOS));
    await store.cargar();
    store.seleccionarCategoria('Pago de servicios');
    store.buscar('tel');
    expect(store.filtrados().map((p) => p.servicio)).toEqual(['Teléfonos de México']);
    expect(store.hayFiltros()).toBe(true);
    store.limpiarFiltros();
    expect(store.hayFiltros()).toBe(false);
    expect(store.filtrados()).toHaveLength(4);
  });

  it('el 404 se trata como vacío, no como error', async () => {
    const { store } = crear(falla('no-encontrado', 404));
    await store.cargar();
    expect(store.estado()).toBe('vacio');
    expect(store.mensajeError()).toBeNull();
  });

  it('una lista vacía es estado vacío', async () => {
    const { store } = crear(ok([]));
    await store.cargar();
    expect(store.estado()).toBe('vacio');
  });

  it('un error muestra solo un mensaje amigable y permite reintentar', async () => {
    const { store, obtenerProductos } = crear(falla('servidor', 500, 'NullPointerException en el API'));
    await store.cargar();
    expect(store.estado()).toBe('error');
    const msg = store.mensajeError() ?? '';
    expect(msg).not.toMatch(/NullPointer|API|servidor|500|token|endpoint/i);

    obtenerProductos.mockResolvedValueOnce(ok(DATOS));
    await store.cargar();
    expect(store.estado()).toBe('listo');
    expect(obtenerProductos).toHaveBeenCalledTimes(2);
  });

  it('no vuelve a pedir el catálogo ya cargado', async () => {
    const { store, obtenerProductos } = crear(ok(DATOS));
    await store.cargar();
    await store.cargar();
    expect(obtenerProductos).toHaveBeenCalledTimes(1);
  });

  it('busca un producto por ids', async () => {
    const { store } = crear(ok(DATOS));
    await store.cargar();
    expect(store.buscarProducto(1, 2)?.servicio).toBe('CFE');
    expect(store.buscarProducto(9, 9)).toBeUndefined();
  });
});
