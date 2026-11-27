/**
 * Qué puede escribir el usuario en cada tipo de campo. Son los mismos límites del backend
 * (DTOs con @Size/@Pattern): lo que no cabe ahí ni siquiera se puede teclear ni pegar.
 */
export type TipoEntrada =
  | 'digitos'
  | 'nombre'
  | 'curp'
  | 'rfc'
  | 'correo'
  | 'usuario'
  | 'password'
  | 'domicilio'
  | 'numeroDomicilio'
  | 'lugar'
  | 'texto'
  | 'importe'
  | 'alfanumerico'
  | 'busqueda';

interface Regla {
  /** Caracteres permitidos (uno por uno). */
  readonly permitido: RegExp;
  /** Longitud máxima si el campo no declara `maxlength`. */
  readonly maximo: number;
  readonly mayusculas?: boolean;
  /** Ajuste del valor completo después de filtrar (espacios dobles, un solo punto, etc.). */
  readonly ajustar?: (valor: string) => string;
}

const LETRA = 'a-zA-ZáéíóúÁÉÍÓÚüÜñÑ';

/** Sin espacio al inicio ni espacios dobles. */
const espaciosSimples = (v: string) => v.replace(/^ +/, '').replace(/ {2,}/g, ' ');

/** Solo dígitos y un punto, con 2 decimales como máximo. */
function importe(v: string, enteros: number): string {
  const [entero = '', ...resto] = v.split('.');
  const parteEntera = entero.replace(/^0+(?=\d)/, '').slice(0, enteros);
  if (resto.length === 0) return parteEntera;
  return `${parteEntera}.${resto.join('').slice(0, 2)}`;
}

export const REGLAS: Record<TipoEntrada, Regla> = {
  digitos: { permitido: /[0-9]/, maximo: 10 },
  nombre: { permitido: new RegExp(`[${LETRA} ]`), maximo: 50, ajustar: espaciosSimples },
  curp: { permitido: /[A-Z0-9]/, maximo: 18, mayusculas: true },
  rfc: { permitido: /[A-ZÑ&0-9]/, maximo: 13, mayusculas: true },
  correo: {
    permitido: /[A-Za-z0-9._%+@-]/,
    maximo: 100,
    ajustar: (v) => {
      const i = v.indexOf('@');
      return i < 0 ? v : v.slice(0, i + 1) + v.slice(i + 1).replace(/@/g, '');
    },
  },
  usuario: { permitido: /[A-Za-z0-9._-]/, maximo: 50 },
  // Cualquier carácter visible, sin espacios ni caracteres de control (política del backend)
  password: { permitido: /[!-~À-ÿ]/, maximo: 72 },
  domicilio: { permitido: new RegExp(`[${LETRA}0-9 .,#'/-]`), maximo: 100, ajustar: espaciosSimples },
  numeroDomicilio: { permitido: /[A-Za-z0-9 /-]/, maximo: 20, mayusculas: true, ajustar: espaciosSimples },
  lugar: { permitido: new RegExp(`[${LETRA} .-]`), maximo: 100, ajustar: espaciosSimples },
  texto: { permitido: new RegExp(`[${LETRA}0-9 .,&()'/-]`), maximo: 100, ajustar: espaciosSimples },
  importe: { permitido: /[0-9.]/, maximo: 9, ajustar: (v) => importe(v, 6) },
  alfanumerico: { permitido: /[A-Za-z0-9]/, maximo: 100 },
  busqueda: { permitido: new RegExp(`[${LETRA}0-9 .,&()'-]`), maximo: 60, ajustar: espaciosSimples },
};

/**
 * Limpia un valor según el tipo: quita lo no permitido, aplica mayúsculas y ajustes, y corta a la longitud máxima.
 * @param enteros dígitos enteros máximos de un importe (por defecto 6)
 */
export function limpiarEntrada(tipo: TipoEntrada, valor: string, maximo?: number, enteros?: number): string {
  const regla = REGLAS[tipo];
  let v = regla.mayusculas ? valor.toUpperCase() : valor;
  v = [...v].filter((c) => regla.permitido.test(c)).join('');
  if (tipo === 'importe' && enteros !== undefined) v = importe(v, enteros);
  else if (regla.ajustar) v = regla.ajustar(v);
  return [...v].slice(0, maximo ?? regla.maximo).join('');
}
