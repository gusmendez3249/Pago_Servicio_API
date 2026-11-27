import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Failure, Result } from '../../../../core/http/result';
import { SessionStore } from '../../../../core/session/session-store';
import {
  TEXTO_ERROR,
  TEXTO_EXPIRADA,
  TEXTO_FORMATO,
  TEXTO_INVALIDA,
  TEXTO_SOLO_CONSULTA,
  TEXTO_VACIA,
} from '../../domain/models/mensajes-referencia';
import { ValidacionReferencia } from '../../domain/models/validacion-referencia';
import { ValidarReferenciaUseCase } from '../../domain/use-cases/validar-referencia.use-case';
import { ProductoReferencia, ValidacionReferenciaStore } from './validacion-referencia.store';

const PRODUCTO: ProductoReferencia = { idSer: 1, idProd: 2, servicio: 'CFE', producto: 'Luz', logoUrl: null, ayudaUrl: null };

function valida(extra: Partial<ValidacionReferencia> = {}): ValidacionReferencia {
  return {
    estado: 'VALIDA', motivo: null, situacion: 'ADEUDO', saldo: 350, idValidacion: 'uuid-1', vigenciaSegundos: 300,
    puedePagar: true, verificacion: 'EN_LINEA', montoAPagar: 350, idSer: 1, idProd: 2, referencia: '0012345678', servicio: 'CFE', producto: 'Luz', ...extra,
  };
}
const ok = (data: ValidacionReferencia): Result<ValidacionReferencia> => ({ ok: true, data, mensaje: 'detalle del backend', httpStatus: 200 });
const falla = (tipo: Failure['tipo'], httpStatus: number): Failure => ({
  ok: false, tipo, httpStatus, codigo: httpStatus, mensaje: 'Error 502 del servidor API token endpoint',
});

describe('ValidacionReferenciaStore', () => {
  let ejecutar: ReturnType<typeof vi.fn>;
  let store: ValidacionReferenciaStore;
  let sesion: SessionStore;

  beforeEach(() => {
    vi.useFakeTimers();
    ejecutar = vi.fn().mockResolvedValue(ok(valida()));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ValidarReferenciaUseCase, useValue: { ejecutar } }],
    });
    sesion = TestBed.inject(SessionStore);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    sesion.iniciar({ token: 't', username: 'u', nombreCliente: 'N', rol: 'CLIENTE' });
    store = TestBed.inject(ValidacionReferenciaStore);
    store.iniciar(PRODUCTO);
  });

  afterEach(() => {
    sesion.cerrar();
    vi.useRealTimers();
  });

  async function validar(texto = '0012345678'): Promise<void> {
    store.editar(texto);
    await store.validar();
  }

  it('inicia en capturando', () => {
    expect(store.estado()).toBe('capturando');
    expect(store.puedeContinuar()).toBe(false);
  });

  it('referencia vacía -> formatoInvalido con el texto exacto, sin llamar', async () => {
    await validar('   ');
    expect(store.estado()).toBe('formatoInvalido');
    expect(store.mensajeFormato()).toBe(TEXTO_VACIA);
    expect(ejecutar).not.toHaveBeenCalled();
  });

  it('formato incorrecto -> formatoInvalido con el texto exacto', async () => {
    await validar('12 34');
    expect(store.estado()).toBe('formatoInvalido');
    expect(store.mensajeFormato()).toBe(TEXTO_FORMATO);
    expect(ejecutar).not.toHaveBeenCalled();
  });

  it('formato correcto -> validando y llama con la referencia recortada', async () => {
    let resolver!: (r: Result<ValidacionReferencia>) => void;
    ejecutar.mockReturnValueOnce(new Promise((r) => (resolver = r)));
    store.editar('  0012345678  ');
    const promesa = store.validar();
    expect(store.estado()).toBe('validando');
    resolver(ok(valida()));
    await promesa;
    expect(ejecutar).toHaveBeenCalledWith(1, 2, '0012345678');
  });

  it('no permite envíos dobles mientras valida', async () => {
    let resolver!: (r: Result<ValidacionReferencia>) => void;
    ejecutar.mockReturnValueOnce(new Promise((r) => (resolver = r)));
    store.editar('0012345678');
    const p1 = store.validar();
    void store.validar();
    resolver(ok(valida()));
    await p1;
    expect(ejecutar).toHaveBeenCalledTimes(1);
  });

  it('VALIDA con adeudo -> valida y habilita continuar', async () => {
    await validar();
    expect(store.estado()).toBe('valida');
    expect(store.puedeContinuar()).toBe(true);
    expect(store.resultado()?.texto).toBe('Referencia verificada. Tienes un adeudo de $350.00.');
    expect(store.datosPago()).toMatchObject({ idSer: 1, idProd: 2, idValidacion: 'uuid-1', saldo: 350 });
  });

  it('textos de sin adeudo y saldo a favor (valor absoluto)', async () => {
    ejecutar.mockResolvedValueOnce(ok(valida({ situacion: 'SIN_ADEUDO', saldo: 0 })));
    await validar();
    expect(store.resultado()?.texto).toBe('Referencia verificada. No tienes adeudo pendiente.');
    ejecutar.mockResolvedValueOnce(ok(valida({ situacion: 'SALDO_A_FAVOR', saldo: -50 })));
    await validar();
    expect(store.resultado()?.texto).toBe('Referencia verificada. Tienes un saldo a favor de $50.00.');
  });

  it('VALIDA sin puedePagar -> soloConsulta, con situación y sin continuar', async () => {
    ejecutar.mockResolvedValueOnce(ok(valida({ puedePagar: false, idValidacion: null })));
    await validar();
    expect(store.estado()).toBe('soloConsulta');
    expect(store.resultado()?.texto).toBe('Referencia verificada. Tienes un adeudo de $350.00.');
    expect(store.resultado()?.detalle).toBe(TEXTO_SOLO_CONSULTA);
    expect(store.puedeContinuar()).toBe(false);
  });

  it('EN_LINEA con adeudo muestra el total a pagar', async () => {
    await validar();
    expect(store.resultado()?.detalle).toBe('Total a pagar: $350.00');
    expect(store.datosPago()).toMatchObject({ verificacion: 'EN_LINEA', montoAPagar: 350 });
  });

  it('SOLO_FORMATO -> valida sin saldo, texto propio, tono no verde y continuar habilitado', async () => {
    ejecutar.mockResolvedValueOnce(
      ok(valida({ verificacion: 'SOLO_FORMATO', situacion: null, saldo: null, montoAPagar: null })),
    );
    await validar();
    expect(store.estado()).toBe('valida');
    expect(store.resultado()).toEqual({
      tono: 'aviso',
      texto: 'No podemos consultar el adeudo de este servicio. Revisa que tu referencia sea correcta antes de continuar.',
      detalle: null,
    });
    expect(store.puedeContinuar()).toBe(true);
    expect(store.datosPago()).toMatchObject({ verificacion: 'SOLO_FORMATO', montoAPagar: null, saldo: null });
  });

  it('EN_LINEA sin adeudo (puedePagar false) -> soloConsulta con "No hay nada por pagar."', async () => {
    ejecutar.mockResolvedValueOnce(
      ok(valida({ situacion: 'SIN_ADEUDO', saldo: 0, montoAPagar: null, puedePagar: false, idValidacion: null })),
    );
    await validar();
    expect(store.estado()).toBe('soloConsulta');
    expect(store.resultado()?.texto).toBe('Referencia verificada. No tienes adeudo pendiente.');
    expect(store.resultado()?.detalle).toBe('No hay nada por pagar.');
    expect(store.puedeContinuar()).toBe(false);
  });

  it('EN_LINEA con saldo a favor (puedePagar false) -> soloConsulta', async () => {
    ejecutar.mockResolvedValueOnce(
      ok(valida({ situacion: 'SALDO_A_FAVOR', saldo: -50, montoAPagar: null, puedePagar: false, idValidacion: null })),
    );
    await validar();
    expect(store.resultado()?.texto).toBe('Referencia verificada. Tienes un saldo a favor de $50.00.');
    expect(store.resultado()?.detalle).toBe('No hay nada por pagar.');
  });

  it('VALIDA con puedePagar pero sin idValidacion no habilita continuar', async () => {
    ejecutar.mockResolvedValueOnce(ok(valida({ idValidacion: null })));
    await validar();
    expect(store.puedeContinuar()).toBe(false);
  });

  it('INVALIDA -> invalida con texto exacto', async () => {
    ejecutar.mockResolvedValueOnce(
      ok(valida({ estado: 'INVALIDA', motivo: 'REFERENCIA_INVALIDA', situacion: null, saldo: null, idValidacion: null, puedePagar: false })),
    );
    await validar();
    expect(store.estado()).toBe('invalida');
    expect(store.resultado()?.texto).toBe(TEXTO_INVALIDA);
    expect(store.puedeContinuar()).toBe(false);
  });

  it.each([
    ['400', falla('validacion', 400)],
    ['404', falla('no-encontrado', 404)],
    ['500', falla('servidor', 500)],
    ['502', falla('servidor', 502)],
    ['503', falla('servidor', 503)],
    ['504', falla('servidor', 504)],
    ['red', falla('red', 0)],
  ])('fallo %s -> error con el texto de la tabla, nunca el del backend', async (_n, f) => {
    ejecutar.mockResolvedValueOnce(f);
    await validar();
    expect(store.estado()).toBe('error');
    expect(store.resultado()?.texto).toBe(TEXTO_ERROR);
    expect(JSON.stringify(store.resultado())).not.toMatch(/servidor|API|token|endpoint|502|backend/i);
  });

  it('respuesta de otra referencia o producto se trata como error', async () => {
    ejecutar.mockResolvedValueOnce(ok(valida({ referencia: 'OTRA' })));
    await validar();
    expect(store.estado()).toBe('error');
  });

  it('los textos mostrados no provienen de la respuesta', async () => {
    ejecutar.mockResolvedValueOnce(ok(valida()));
    await validar();
    expect(store.resultado()?.texto).not.toContain('detalle del backend');
  });

  it('se agota la vigencia -> expirada y se descarta el comprobante', async () => {
    await validar();
    vi.advanceTimersByTime(299_000);
    expect(store.estado()).toBe('valida');
    vi.advanceTimersByTime(1_500);
    expect(store.estado()).toBe('expirada');
    expect(store.resultado()?.texto).toBe(TEXTO_EXPIRADA);
    expect(store.datosPago()).toBeNull();
    expect(store.puedeContinuar()).toBe(false);
  });

  it('editar un solo carácter invalida y vuelve a capturando', async () => {
    await validar();
    store.editar('0012345679');
    expect(store.estado()).toBe('capturando');
    expect(store.datosPago()).toBeNull();
    expect(store.validacion()).toBeNull();
    vi.advanceTimersByTime(400_000);
    expect(store.estado()).toBe('capturando');
  });

  it.each([
    ['invalida', () => ok(valida({ estado: 'INVALIDA', idValidacion: null, puedePagar: false }))],
    ['error', () => falla('red', 0)],
  ])('desde %s editar -> capturando y reintentar -> validando', async (_n, respuesta) => {
    ejecutar.mockResolvedValueOnce(respuesta());
    await validar();
    let resolver!: (r: Result<ValidacionReferencia>) => void;
    ejecutar.mockReturnValueOnce(new Promise((r) => (resolver = r)));
    const p = store.reintentar();
    expect(store.estado()).toBe('validando');
    resolver(ok(valida()));
    await p;
    expect(store.estado()).toBe('valida');
    store.editar('X');
    expect(store.estado()).toBe('capturando');
  });

  it('cambiar de producto limpia la captura y la validación', async () => {
    await validar();
    store.iniciar({ ...PRODUCTO, idProd: 3 });
    expect(store.texto()).toBe('');
    expect(store.datosPago()).toBeNull();
    expect(store.estado()).toBe('capturando');
  });

  it('una respuesta atrasada tras editar se ignora', async () => {
    let resolver!: (r: Result<ValidacionReferencia>) => void;
    ejecutar.mockReturnValueOnce(new Promise((r) => (resolver = r)));
    store.editar('0012345678');
    const p = store.validar();
    store.reiniciar();
    resolver(ok(valida()));
    await p;
    expect(store.datosPago()).toBeNull();
  });

  it('cerrar sesión limpia todo', async () => {
    await validar();
    sesion.cerrar();
    TestBed.tick();
    expect(store.datosPago()).toBeNull();
    expect(store.texto()).toBe('');
    expect(store.producto()).toBeNull();
  });

  it('401 (expulsar) limpia todo', async () => {
    await validar();
    sesion.expulsar('aviso');
    TestBed.tick();
    expect(store.datosPago()).toBeNull();
    expect(store.estado()).toBe('capturando');
  });

  it('no persiste nada en el navegador', async () => {
    localStorage.clear();
    sessionStorage.clear();
    await validar();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
