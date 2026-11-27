import { limpiarEntrada } from './reglas-entrada';

describe('limpiarEntrada', () => {
  it('dígitos: quita letras y corta a la longitud', () => {
    expect(limpiarEntrada('digitos', '41a8-12 34567890', 10)).toBe('4181234567');
    expect(limpiarEntrada('digitos', '12345', 4)).toBe('1234');
  });

  it('nombre: solo letras con acentos y espacios simples', () => {
    expect(limpiarEntrada('nombre', '  José  María<1>')).toBe('José María');
    expect(limpiarEntrada('nombre', 'a'.repeat(80)).length).toBe(50);
  });

  it('CURP y RFC: mayúsculas y alfanuméricos', () => {
    expect(limpiarEntrada('curp', 'garl-950312mgtjpd01xx')).toBe('GARL950312MGTJPD01');
    expect(limpiarEntrada('rfc', 'ñañ&950312 qa1')).toBe('ÑAÑ&950312QA1');
  });

  it('correo: un solo @ y sin espacios', () => {
    expect(limpiarEntrada('correo', 'ana @mail@x.com')).toBe('ana@mailx.com');
  });

  it('usuario y contraseña sin espacios ni caracteres de control', () => {
    expect(limpiarEntrada('usuario', 'ana garcía!')).toBe('anagarca');
    expect(limpiarEntrada('password', 'Ab c#1\u0000\t')).toBe('Abc#1');
  });

  it('importe: un punto, 2 decimales y enteros limitados', () => {
    expect(limpiarEntrada('importe', '0012a.3.456')).toBe('12.34');
    expect(limpiarEntrada('importe', '1234567', undefined, 5)).toBe('12345');
    expect(limpiarEntrada('importe', '.5')).toBe('.5');
  });

  it('referencia alfanumérica y búsqueda sin etiquetas', () => {
    expect(limpiarEntrada('alfanumerico', 'AB-12 cd')).toBe('AB12cd');
    expect(limpiarEntrada('busqueda', '<img src=x>CFE')).toBe('img srcxCFE');
  });

  it('domicilio y número exterior', () => {
    expect(limpiarEntrada('domicilio', 'Av. Hidalgo #12 <b>')).toBe('Av. Hidalgo #12 b');
    expect(limpiarEntrada('numeroDomicilio', '12-b interior largo de más')).toBe('12-B INTERIOR LARGO ');
  });
});
