import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../../../core/session/session-store';
import { ElementoHistorial, EstatusFiltro, PaginaHistorial, SalidaHistorial } from '../../domain/models/historial';
import { HistorialRepository } from '../../domain/repository/historial-repository';
import { HistorialStore } from './historial.store';

export function el(upc: string, p: Partial<ElementoHistorial> = {}): ElementoHistorial {
  return {
    numCuentaEnmascarada: null, upc, estatus: 'CONFIRMADA', motivo: null, monto: 10, saldoRestante: null, folio: null, idTx: null,
    identificadorOperacion: null, pinServicio: null, leyenda: null, servicio: 'CFE', producto: 'Luz', referenciaEnmascarada: null,
    telefonoEnmascarado: null, fechaTransaccion: null, ...p,
  };
}
const pagina = (elementos: ElementoHistorial[], n: number, hayMas: boolean): SalidaHistorial => ({
  tipo: 'pagina',
  pagina: { elementos, pagina: n, tamanio: 20, total: elementos.length, hayMas } satisfies PaginaHistorial,
});

describe('HistorialStore', () => {
  let obtenerPagina: ReturnType<typeof vi.fn>;
  let sesion: SessionStore;
  let store: HistorialStore;

  beforeEach(() => {
    obtenerPagina = vi.fn().mockResolvedValue(pagina([], 0, false));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: HistorialRepository, useValue: { obtenerPagina, obtenerDetalle: vi.fn() } }],
    });
    sesion = TestBed.inject(SessionStore);
    sesion.iniciar({ token: 't', username: 'u', nombreCliente: 'N', rol: 'CLIENTE' });
    store = TestBed.inject(HistorialStore);
  });
  afterEach(() => sesion.cerrar());

  it('carga y pasa a listo', async () => {
    obtenerPagina.mockResolvedValue(pagina([el('A')], 0, false));
    const p = store.cargar();
    expect(store.estado()).toBe('cargando');
    await p;
    expect(store.estado()).toBe('listo');
    expect(store.elementos()).toHaveLength(1);
    expect(obtenerPagina).toHaveBeenCalledWith(null, 0, 20);
  });

  it('sin pagos queda vacío', async () => {
    await store.cargar();
    expect(store.estado()).toBe('vacio');
  });

  it('error y reintento', async () => {
    obtenerPagina.mockResolvedValueOnce({ tipo: 'generico' });
    await store.cargar();
    expect(store.estado()).toBe('error');
    obtenerPagina.mockResolvedValueOnce(pagina([el('A')], 0, false));
    await store.cargar();
    expect(store.estado()).toBe('listo');
  });

  it('"Ver más" acumula, respeta hayMas y no duplica', async () => {
    obtenerPagina.mockResolvedValueOnce(pagina([el('A'), el('B')], 0, true));
    await store.cargar();
    expect(store.hayMas()).toBe(true);
    obtenerPagina.mockResolvedValueOnce(pagina([el('B'), el('C')], 1, false));
    await store.verMas();
    expect(obtenerPagina).toHaveBeenLastCalledWith(null, 1, 20);
    expect(store.elementos().map((e) => e.upc)).toEqual(['A', 'B', 'C']);
    expect(store.hayMas()).toBe(false);
    await store.verMas();
    expect(obtenerPagina).toHaveBeenCalledTimes(2);
  });

  it('un fallo al pedir más conserva lo cargado y permite reintentar', async () => {
    obtenerPagina.mockResolvedValueOnce(pagina([el('A')], 0, true));
    await store.cargar();
    obtenerPagina.mockResolvedValueOnce({ tipo: 'sinRed' });
    await store.verMas();
    expect(store.errorMas()).toBe(true);
    expect(store.elementos()).toHaveLength(1);
    obtenerPagina.mockResolvedValueOnce(pagina([el('B')], 1, false));
    await store.verMas();
    expect(store.errorMas()).toBe(false);
    expect(store.elementos()).toHaveLength(2);
  });

  it('cambiar de filtro reinicia la página', async () => {
    obtenerPagina.mockResolvedValueOnce(pagina([el('A')], 0, true));
    await store.cargar();
    obtenerPagina.mockResolvedValueOnce(pagina([el('R', { estatus: 'RECHAZADA' })], 0, false));
    await store.seleccionarFiltro('rechazados');
    expect(obtenerPagina).toHaveBeenLastCalledWith('RECHAZADA' as EstatusFiltro, 0, 20);
    expect(store.elementos().map((e) => e.upc)).toEqual(['R']);
  });

  it('Pendientes combina PENDIENTE e INDETERMINADA ordenados por fecha', async () => {
    obtenerPagina.mockImplementation(async (estatus: EstatusFiltro) =>
      estatus === 'PENDIENTE'
        ? pagina([el('P1', { fechaTransaccion: '2026-11-10T10:00:00' })], 0, false)
        : pagina([el('I1', { fechaTransaccion: '2026-11-12T10:00:00' })], 0, false),
    );
    await store.seleccionarFiltro('pendientes');
    expect(store.elementos().map((e) => e.upc)).toEqual(['I1', 'P1']);
  });

  it('vacío con filtro activo conserva el filtro', async () => {
    await store.seleccionarFiltro('exitosos');
    expect(store.estado()).toBe('vacio');
    expect(store.filtro()).toBe('exitosos');
  });

  it('se limpia al cerrar sesión', async () => {
    obtenerPagina.mockResolvedValueOnce(pagina([el('A')], 0, true));
    await store.seleccionarFiltro('exitosos');
    sesion.cerrar();
    TestBed.tick();
    expect(store.elementos()).toEqual([]);
    expect(store.filtro()).toBe('todos');
    expect(store.hayMas()).toBe(false);
  });

  it('ignora una respuesta atrasada tras cerrar sesión', async () => {
    let resolver!: (s: SalidaHistorial) => void;
    obtenerPagina.mockReturnValueOnce(new Promise<SalidaHistorial>((r) => (resolver = r)));
    const p = store.cargar();
    sesion.cerrar();
    TestBed.tick();
    resolver(pagina([el('A')], 0, false));
    await p;
    expect(store.elementos()).toEqual([]);
  });

  it('no escribe nada en storage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    obtenerPagina.mockResolvedValueOnce(pagina([el('A')], 0, false));
    await store.cargar();
    expect(setItem).not.toHaveBeenCalled();
    setItem.mockRestore();
  });
});
