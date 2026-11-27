import { validarFormatoReferencia } from './formato-referencia';

describe('validarFormatoReferencia', () => {
  it('rechaza vacía y solo espacios', () => {
    expect(validarFormatoReferencia('')).toEqual({ valida: false, error: 'vacia' });
    expect(validarFormatoReferencia('   ')).toEqual({ valida: false, error: 'vacia' });
  });

  it('recorta espacios en los extremos', () => {
    expect(validarFormatoReferencia('  0012AB  ')).toEqual({ valida: true, referencia: '0012AB' });
  });

  it('rechaza espacios internos y símbolos', () => {
    expect(validarFormatoReferencia('12 34')).toEqual({ valida: false, error: 'formato' });
    expect(validarFormatoReferencia('12-34')).toEqual({ valida: false, error: 'formato' });
    expect(validarFormatoReferencia('añ12')).toEqual({ valida: false, error: 'formato' });
  });

  it('admite 100 caracteres y rechaza 101', () => {
    expect(validarFormatoReferencia('a'.repeat(100)).valida).toBe(true);
    expect(validarFormatoReferencia('a'.repeat(101))).toEqual({ valida: false, error: 'formato' });
  });
});
