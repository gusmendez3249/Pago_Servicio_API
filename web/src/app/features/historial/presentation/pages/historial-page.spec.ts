import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthRepository } from '../../../auth/domain/repository/auth-repository';
import { SessionStore } from '../../../../core/session/session-store';
import { ElementoHistorial, SalidaHistorial } from '../../domain/models/historial';
import { HistorialRepository } from '../../domain/repository/historial-repository';
import { el } from '../store/historial.store.spec';
import { DetallePagoPage } from './detalle-pago-page';
import { HistorialPage } from './historial-page';
import { SalidaEstado } from '../../../pago/domain/models/pago';

function preparar(obtenerPagina: () => Promise<SalidaHistorial>, obtenerDetalle: () => Promise<SalidaEstado> = async () => ({ tipo: 'generico' })) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: AuthRepository, useValue: {} },
      { provide: HistorialRepository, useValue: { obtenerPagina, obtenerDetalle } },
    ],
  });
  TestBed.inject(SessionStore).iniciar({ token: 't', username: 'u', nombreCliente: 'N', rol: 'CLIENTE' });
}
const lista = (e: ElementoHistorial[], hayMas = false): SalidaHistorial => ({
  tipo: 'pagina',
  pagina: { elementos: e, pagina: 0, tamanio: 20, total: e.length, hayMas },
});
async function pintar<T>(tipo: new () => T) {
  const f = TestBed.createComponent(tipo);
  for (let i = 0; i < 3; i++) {
    await f.whenStable();
    f.detectChanges();
  }
  return (f.nativeElement as HTMLElement).textContent ?? '';
}

describe('páginas del historial', () => {
  afterEach(() => TestBed.inject(SessionStore).cerrar());

  it('muestra renglones con los textos de la especificación', async () => {
    preparar(async () =>
      lista([
        el('A', { monto: 100, folio: 'F1', fechaTransaccion: new Date(2026, 10, 13, 19, 39).toISOString(), telefonoEnmascarado: '******4567' }),
        el('B', { estatus: 'RECHAZADA', identificadorOperacion: 'OP2' }),
        el('C', { estatus: 'INDETERMINADA' }),
      ], true),
    );
    const t = await pintar(HistorialPage);
    for (const s of ['Mis pagos', 'Actualizar', 'Todos', 'Exitosos', 'Rechazados', 'Pendientes', 'Exitoso', 'Rechazado', 'Folio F1', 'Número de operación OP2', '13 nov 2026', '19:39', '******4567', 'Ver más'])
      expect(t).toContain(s);
    expect(t).toContain('$100.00');
  });

  it('vacío', async () => {
    preparar(async () => lista([]));
    const t = await pintar(HistorialPage);
    expect(t).toContain('Aún no tienes pagos');
    expect(t).toContain('Ir al catálogo');
  });

  it('error con texto amigable y sin detalles técnicos', async () => {
    preparar(async () => ({ tipo: 'generico' }));
    const t = await pintar(HistorialPage);
    expect(t).toContain('No pudimos cargar tus pagos. Inténtalo de nuevo.');
    expect(t).toContain('Reintentar');
  });

  it('detalle encontrado muestra el comprobante con estado, motivo, logo y código', async () => {
    preparar(async () => lista([]), async () => ({
      tipo: 'pago',
      pago: el('U1', { estatus: 'RECHAZADA', motivo: 'REFERENCIA_INVALIDA', folio: 'F9' }),
    }));
    const t = await pintar(DetallePagoPage);
    for (const s of ['Comprobante', 'No aplicado', 'No se reconoció la referencia. Revísala en tu recibo.', 'Folio', 'F9', 'Volver a mis pagos'])
      expect(t).toContain(s);
    expect(t).not.toContain('Consultar de nuevo');
  });

  it('detalle pendiente ofrece consultar de nuevo', async () => {
    preparar(async () => lista([]), async () => ({ tipo: 'pago', pago: el('U1', { estatus: 'PENDIENTE' }) }));
    const t = await pintar(DetallePagoPage);
    expect(t).toContain('Pendiente de confirmar');
    expect(t).toContain('Consultar de nuevo');
  });

  it('detalle 404', async () => {
    preparar(async () => lista([]), async () => ({ tipo: 'noEncontrado' }));
    const t = await pintar(DetallePagoPage);
    expect(t).toContain('No encontramos esa operación');
    expect(t).toContain('Volver a mis pagos');
  });
});
