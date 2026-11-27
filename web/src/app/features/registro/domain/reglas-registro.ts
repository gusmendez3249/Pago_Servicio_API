/**
 * Reglas del registro, idénticas a las del backend (DatosPersonalesDto, LoginCredencialesDto, @IdentidadCongruente):
 * la pantalla avisa antes de enviar. El backend vuelve a validar todo.
 */

const LETRAS = 'a-zA-ZáéíóúÁÉÍÓÚüÜñÑ';
export const PATRON_NOMBRE = new RegExp(`^[${LETRAS}]+( [${LETRAS}]+)*$`);
const MES_DIA = '(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])';
const ENTIDADES = '(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)';
export const PATRON_CURP = new RegExp(`^[A-Z]{4}[0-9]{2}${MES_DIA}[HM]${ENTIDADES}[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$`);
export const PATRON_RFC = new RegExp(`^[A-ZÑ&]{3,4}[0-9]{2}${MES_DIA}[A-Z0-9]{3}$`);
export const PATRON_CORREO = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
export const PATRON_USUARIO = /^[A-Za-z0-9._-]{3,50}$/;
export const PATRON_TEXTO_SEGURO = /^[^<>\u0000-\u001f\u007f]*$/;

const DICCIONARIO_CURP = '0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ';

export function nombreValido(valor: string, minimo = 3): boolean {
  const v = valor.trim();
  return v.length >= minimo && v.length <= 50 && PATRON_NOMBRE.test(v);
}

/** Dígito verificador oficial de la CURP. */
export function digitoCurpValido(curp: string): boolean {
  if (curp.length !== 18) return false;
  let suma = 0;
  for (let i = 0; i < 17; i++) {
    const valor = DICCIONARIO_CURP.indexOf(curp[i]);
    if (valor < 0) return false;
    suma += valor * (18 - i);
  }
  return curp[17] === String((10 - (suma % 10)) % 10);
}

/** yyMMdd de una fecha 'yyyy-mm-dd'. */
function yymmdd(fecha: string): string {
  return fecha.slice(2, 4) + fecha.slice(5, 7) + fecha.slice(8, 10);
}

export type ErrorCurp = 'formato' | 'digito' | 'fecha' | 'sexo' | null;

export function errorCurp(curp: string, fechaNacimiento: string, sexo: string): ErrorCurp {
  if (!PATRON_CURP.test(curp)) return 'formato';
  if (!digitoCurpValido(curp)) return 'digito';
  if (fechaNacimiento && curp.slice(4, 10) !== yymmdd(fechaNacimiento)) return 'fecha';
  const letra = curp[10];
  if ((sexo === 'MASCULINO' && letra !== 'H') || (sexo === 'FEMENINO' && letra !== 'M')) return 'sexo';
  return null;
}

export type ErrorRfc = 'formato' | 'generico' | 'curp' | null;

export function errorRfc(rfc: string, curp: string): ErrorRfc {
  if (!PATRON_RFC.test(rfc)) return 'formato';
  if (rfc.startsWith('XAXX') || rfc.startsWith('XEXX')) return 'generico';
  if (curp.length >= 10) {
    const inicio = rfc.length === 13 ? 4 : 3;
    if (rfc[0] !== curp[0] || rfc.slice(inicio, inicio + 6) !== curp.slice(4, 10)) return 'curp';
  }
  return null;
}

/** Edad cumplida a hoy; null si la fecha no es válida. */
export function edad(fechaNacimiento: string, hoy = new Date()): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fechaNacimiento);
  if (!m) return null;
  const [a, mes, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const fecha = new Date(a, mes - 1, d);
  if (fecha.getMonth() !== mes - 1 || fecha.getDate() !== d) return null;
  let anios = hoy.getFullYear() - a;
  if (hoy.getMonth() < mes - 1 || (hoy.getMonth() === mes - 1 && hoy.getDate() < d)) anios--;
  return anios;
}

/** Misma política que el backend: 8 a 72, mayúscula, minúscula, número y carácter especial, sin espacios. */
export function passwordValida(valor: string): boolean {
  return (
    valor.length >= 8 &&
    valor.length <= 72 &&
    /[a-z]/.test(valor) &&
    /[A-Z]/.test(valor) &&
    /\d/.test(valor) &&
    /[!-/:-@[-`{-~]/.test(valor) &&
    !/[\s\u0000-\u001f\u007f]/.test(valor)
  );
}

/** PIN trivial: todos iguales (1111) o consecutivos (1234, 4321). */
export function pinTrivial(pin: string): boolean {
  if (!/^\d{4}$/.test(pin)) return false;
  const d = [...pin].map(Number);
  const iguales = d.every((x) => x === d[0]);
  const sube = d.every((x, i) => i === 0 || x === d[i - 1] + 1);
  const baja = d.every((x, i) => i === 0 || x === d[i - 1] - 1);
  return iguales || sube || baja;
}
