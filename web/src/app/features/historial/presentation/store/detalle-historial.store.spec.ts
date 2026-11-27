import { TestBed } from '@angular/core/testing';
import { SalidaEstado } from '../../../pago/domain/models/pago';
import { HistorialRepository } from '../../domain/repository/historial-repository';
import { DetalleHistorialStore } from './detalle-historial.store';
import { el } from './historial.store.spec';

describe('DetalleHistorialStore', () => {
  let obtenerDetalle: ReturnType<typeof vi.fn>;
  let store: DetalleHistorialStore;

  beforeEach(() => {
    obtenerDetalle = vi.fn();
    TestBed.configureTestingModule({
      providers: [DetalleHistorialStore, { provide: HistorialRepository, useValue: { obtenerDetalle, obtenerPagina: vi.fn() } }],
    });
    store = TestBed.inject(DetalleHistorialStore);
  });

  it('encontrado', async () => {
    obtenerDetalle.mockResolvedValue({ tipo: 'pago', pago: el('U1') } satisfies SalidaEstado);
    await store.cargar('U1');
    expect(store.estado()).toBe('listo');
    expect(obtenerDetalle).toHaveBeenCalledWith('U1');
  });

  it('no encontrado', async () => {
    obtenerDetalle.mockResolvedValue({ tipo: 'noEncontrado' } satisfies SalidaEstado);
    await store.cargar('X');
    expect(store.estado()).toBe('noEncontrado');
  });

  it('error y reintento', async () => {
    obtenerDetalle.mockResolvedValueOnce({ tipo: 'generico' });
    await store.cargar('U1');
    expect(store.estado()).toBe('error');
    obtenerDetalle.mockResolvedValueOnce({ tipo: 'pago', pago: el('U1') });
    await store.reintentar();
    expect(store.estado()).toBe('listo');
  });

  it('pendiente: consultar de nuevo actualiza y, si falla, conserva el comprobante', async () => {
    obtenerDetalle.mockResolvedValueOnce({ tipo: 'pago', pago: el('U1', { estatus: 'PENDIENTE' }) });
    await store.cargar('U1');
    obtenerDetalle.mockResolvedValueOnce({ tipo: 'sinRed' });
    await store.consultarDeNuevo();
    expect(store.pago()?.estatus).toBe('PENDIENTE');
    obtenerDetalle.mockResolvedValueOnce({ tipo: 'pago', pago: el('U1', { estatus: 'CONFIRMADA' }) });
    await store.consultarDeNuevo();
    expect(store.pago()?.estatus).toBe('CONFIRMADA');
    expect(store.consultando()).toBe(false);
  });
});
