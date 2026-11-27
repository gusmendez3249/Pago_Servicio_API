import { aPagina } from './historial-dto';
import { interpretarHistorial } from '../repository-impl/historial-repository-impl';

describe('historial dto', () => {
  it('tolera nulos y elimina pinServicio y saldoRestante', () => {
    const p = aPagina({
      elementos: [{ upc: 'A', estatus: 'CONFIRMADA', pinServicio: '1234', saldoRestante: 5, logoUrl: 'https://x/y.png', idServicio: 3 }, null, 'x'],
      pagina: 0, tamanio: 20, total: 1, hayMas: false,
    });
    expect(p?.elementos).toHaveLength(1);
    expect(p?.elementos[0]).toMatchObject({ upc: 'A', pinServicio: null, saldoRestante: null, logoUrl: 'https://x/y.png', idServicio: 3 });
  });

  it('una respuesta vacía es una página vacía; un cuerpo inválido es genérico', () => {
    expect(aPagina({})).toMatchObject({ elementos: [], hayMas: false });
    expect(interpretarHistorial({ ok: true, data: null as never, mensaje: '', httpStatus: 200 })).toEqual({ tipo: 'generico' });
  });

  it('traduce 401 y falta de red', () => {
    const f = (httpStatus: number, tipo: 'red' | 'servidor') => ({ ok: false as const, tipo, httpStatus, codigo: httpStatus, mensaje: 'x' });
    expect(interpretarHistorial(f(401, 'servidor'))).toEqual({ tipo: 'noAutenticado' });
    expect(interpretarHistorial(f(0, 'red'))).toEqual({ tipo: 'sinRed' });
    expect(interpretarHistorial(f(500, 'servidor'))).toEqual({ tipo: 'generico' });
  });
});
