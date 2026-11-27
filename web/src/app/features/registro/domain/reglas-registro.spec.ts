import { digitoCurpValido, edad, errorCurp, errorRfc, nombreValido, passwordValida, pinTrivial } from './reglas-registro';

describe('reglas del registro', () => {
  it('valida el dígito verificador de la CURP', () => {
    expect(digitoCurpValido('PERJ950515HDFRMN06')).toBe(true);
    expect(digitoCurpValido('PERJ950515HDFRMN01')).toBe(false);
  });
  it('la CURP debe coincidir con fecha y sexo', () => {
    expect(errorCurp('PERJ950515HDFRMN06', '1995-05-15', 'MASCULINO')).toBeNull();
    expect(errorCurp('PERJ950515HDFRMN06', '1985-05-15', 'MASCULINO')).toBe('fecha');
    expect(errorCurp('PERJ950515HDFRMN06', '1995-05-15', 'FEMENINO')).toBe('sexo');
    expect(errorCurp('perj', '1995-05-15', 'MASCULINO')).toBe('formato');
  });
  it('el RFC debe corresponder a la CURP y no ser genérico', () => {
    expect(errorRfc('PERJ9505151A2', 'PERJ950515HDFRMN06')).toBeNull();
    expect(errorRfc('XAXX950515AB1', 'PERJ950515HDFRMN06')).toBe('generico');
    expect(errorRfc('LOPJ8505151A2', 'PERJ950515HDFRMN06')).toBe('curp');
  });
  it('calcula la edad cumplida', () => {
    const hoy = new Date(2026, 10, 27);
    expect(edad('2008-11-27', hoy)).toBe(18);
    expect(edad('2008-11-28', hoy)).toBe(17);
    expect(edad('2008-02-30', hoy)).toBeNull();
  });
  it('contraseña, PIN y nombre siguen la política del backend', () => {
    expect(passwordValida('Password123!')).toBe(true);
    expect(passwordValida('password123')).toBe(false);
    expect(passwordValida('Pass word1!')).toBe(false);
    expect(pinTrivial('1111')).toBe(true);
    expect(pinTrivial('4321')).toBe(true);
    expect(pinTrivial('4826')).toBe(false);
    expect(nombreValido('Ana')).toBe(true);
    expect(nombreValido('An')).toBe(false);
    expect(nombreValido('Ana  María')).toBe(false);
  });
});
