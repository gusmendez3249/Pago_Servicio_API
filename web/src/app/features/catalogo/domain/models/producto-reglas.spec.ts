import { categoriaDe, formatoMoneda, importeDe, inicialesDe, normalizarTexto } from './producto-reglas';

describe('importeDe', () => {
  it('tipoFront 2: el precio es la comisión', () => {
    expect(importeDe({ tipoFront: 2, precio: 12 })).toEqual({ etiqueta: 'Comisión', monto: 12 });
  });
  it('tipoFront 40 (envío de dinero): el precio también es la comisión', () => {
    expect(importeDe({ tipoFront: 40, precio: 12 })).toEqual({ etiqueta: 'Comisión', monto: 12 });
  });
  it('otro tipoFront o nulo: es el precio', () => {
    expect(importeDe({ tipoFront: 1, precio: 50 })).toEqual({ etiqueta: 'Precio', monto: 50 });
    expect(importeDe({ tipoFront: null, precio: 0 })).toEqual({ etiqueta: 'Precio', monto: 0 });
  });
  it('sin precio no hay importe', () => {
    expect(importeDe({ tipoFront: 2, precio: null })).toBeNull();
  });
});

describe('formatoMoneda', () => {
  it('da formato MXN', () => {
    expect(formatoMoneda(1234.5)).toContain('1,234.50');
    expect(formatoMoneda(1234.5)).toContain('$');
  });
});

describe('inicialesDe', () => {
  it('toma la inicial de las dos primeras palabras', () => {
    expect(inicialesDe('Telefonos de México')).toBe('TD');
    expect(inicialesDe('  izzi  telecom ')).toBe('IT');
  });
  it('con una palabra toma dos letras', () => {
    expect(inicialesDe('cfe')).toBe('CF');
    expect(inicialesDe('X')).toBe('X');
  });
  it('vacío o nulo da signo de interrogación', () => {
    expect(inicialesDe('')).toBe('?');
    expect(inicialesDe(null)).toBe('?');
    expect(inicialesDe(' - ')).toBe('?');
  });
});

describe('normalizarTexto / categoriaDe', () => {
  it('quita acentos y mayúsculas', () => {
    expect(normalizarTexto('  TeLÉfonos Ñandú ')).toBe('telefonos nandu');
  });
  it('categoría nula cae en Otros', () => {
    expect(categoriaDe({ categoria: null })).toBe('Otros');
    expect(categoriaDe({ categoria: 'Recargas' })).toBe('Recargas');
  });
});
