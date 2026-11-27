import { FormControl } from '@angular/forms';
import { validadoresPassword, validadoresUsuario } from './validadores-login';

const usuario = (v: string) => new FormControl(v, validadoresUsuario);
const password = (v: string) => new FormControl(v, validadoresPassword);

describe('validadores de login', () => {
  it('usuario: obligatorio', () => {
    expect(usuario('').errors).toHaveProperty('required');
  });

  it('usuario: 3 a 50 caracteres', () => {
    expect(usuario('ab').errors).toHaveProperty('minlength');
    expect(usuario('abc').valid).toBe(true);
    expect(usuario('a'.repeat(50)).valid).toBe(true);
    expect(usuario('a'.repeat(51)).errors).toHaveProperty('maxlength');
  });

  it('usuario: solo [A-Za-z0-9._-]', () => {
    expect(usuario('Juan.Perez_1-x').valid).toBe(true);
    expect(usuario('juan perez').errors).toHaveProperty('pattern');
    expect(usuario('juan@mail').errors).toHaveProperty('pattern');
  });

  it('contraseña: obligatoria y de 8 a 72', () => {
    expect(password('').errors).toHaveProperty('required');
    expect(password('1234567').errors).toHaveProperty('minlength');
    expect(password('x'.repeat(8)).valid).toBe(true);
    expect(password('x'.repeat(72)).valid).toBe(true);
    expect(password('x'.repeat(73)).errors).toHaveProperty('maxlength');
  });
});
