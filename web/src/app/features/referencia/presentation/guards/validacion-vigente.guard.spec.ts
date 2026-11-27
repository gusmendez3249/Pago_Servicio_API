import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree, convertToParamMap, provideRouter } from '@angular/router';
import { Result } from '../../../../core/http/result';
import { SessionStore } from '../../../../core/session/session-store';
import { ValidacionReferencia } from '../../domain/models/validacion-referencia';
import { ValidarReferenciaUseCase } from '../../domain/use-cases/validar-referencia.use-case';
import { ValidacionReferenciaStore } from '../store/validacion-referencia.store';
import { validacionVigenteGuard } from './validacion-vigente.guard';

const respuesta: ValidacionReferencia = {
  estado: 'VALIDA', motivo: null, situacion: 'ADEUDO', saldo: 10, idValidacion: 'u', vigenciaSegundos: 60,
  puedePagar: true, verificacion: 'EN_LINEA', montoAPagar: 10, idSer: 1, idProd: 2, referencia: 'ABC', servicio: 'S', producto: 'P',
};

describe('validacionVigenteGuard', () => {
  let store: ValidacionReferenciaStore;
  let sesion: SessionStore;
  let ejecutar: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    ejecutar = vi.fn().mockResolvedValue({ ok: true, data: respuesta, mensaje: '', httpStatus: 200 } as Result<ValidacionReferencia>);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ValidarReferenciaUseCase, useValue: { ejecutar } }],
    });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    sesion = TestBed.inject(SessionStore);
    sesion.iniciar({ token: 't', username: 'u', nombreCliente: 'N', rol: 'CLIENTE' });
    store = TestBed.inject(ValidacionReferenciaStore);
    store.iniciar({ idSer: 1, idProd: 2, servicio: 'S', producto: 'P', logoUrl: null, ayudaUrl: null });
  });

  afterEach(() => {
    sesion.cerrar();
    vi.useRealTimers();
  });

  function ejecutarGuard(idServicio = '1', idProducto = '2') {
    const ruta = { paramMap: convertToParamMap({ idServicio, idProducto }) } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => validacionVigenteGuard(ruta, {} as never));
  }

  async function validar(): Promise<void> {
    store.editar('ABC');
    await store.validar();
  }

  it('sin validación redirige a la captura del producto', () => {
    const r = ejecutarGuard() as UrlTree;
    expect(r.toString()).toBe('/catalogo/1/2/referencia');
  });

  it('con validación vigente del mismo producto permite el paso', async () => {
    await validar();
    expect(ejecutarGuard()).toBe(true);
  });

  it('de otro producto redirige a la captura de ese producto', async () => {
    await validar();
    expect((ejecutarGuard('1', '9') as UrlTree).toString()).toBe('/catalogo/1/9/referencia');
  });

  it('expirada redirige', async () => {
    await validar();
    vi.advanceTimersByTime(61_000);
    expect(ejecutarGuard()).toBeInstanceOf(UrlTree);
  });

  it('solo consulta (sin comprobante) redirige', async () => {
    ejecutar.mockResolvedValueOnce({ ok: true, data: { ...respuesta, puedePagar: false, idValidacion: null }, mensaje: '', httpStatus: 200 });
    await validar();
    expect(ejecutarGuard()).toBeInstanceOf(UrlTree);
  });
});
