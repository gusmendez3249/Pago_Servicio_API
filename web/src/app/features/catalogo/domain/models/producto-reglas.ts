import { Producto } from './producto';

/** `tipoFront` 2: el precio es la comisión y el monto lo captura el usuario después. */
export const TIPO_FRONT_COMISION = 2;
/** `tipoFront` 40 (envíos de dinero): el precio del catálogo también es la comisión, no el monto enviado. */
export const TIPO_FRONT_ENVIO_DINERO = 40;
/** `tipoFront` cuyo precio de catálogo se muestra como comisión. */
export const TIPOS_FRONT_COMISION: readonly number[] = [TIPO_FRONT_COMISION, TIPO_FRONT_ENVIO_DINERO];
/** `tipoFront` de los productos cuyo pago pasa por captura y validación de referencia. */
export const TIPOS_FRONT_REFERENCIA: readonly number[] = [2, 4];

export function admiteReferencia(p: Pick<Producto, 'tipoFront'>): boolean {
  return p.tipoFront !== null && TIPOS_FRONT_REFERENCIA.includes(p.tipoFront);
}

/** `tipoFront` 1: precio fijo y teléfono; se paga directo, sin validación de referencia. */
export const TIPO_FRONT_PRECIO_FIJO = 1;

export function admitePagoDirecto(p: Pick<Producto, 'tipoFront' | 'precio'>): boolean {
  return p.tipoFront === TIPO_FRONT_PRECIO_FIJO && p.precio !== null && p.precio > 0;
}

export const CATEGORIA_SIN_NOMBRE = 'Otros';

export interface Importe {
  etiqueta: 'Comisión' | 'Precio';
  monto: number;
}

/** Importe a mostrar según la regla de negocio; null si no hay precio. */
export function importeDe(p: Pick<Producto, 'tipoFront' | 'precio'>): Importe | null {
  if (p.precio === null) return null;
  const esComision = p.tipoFront !== null && TIPOS_FRONT_COMISION.includes(p.tipoFront);
  return { etiqueta: esComision ? 'Comisión' : 'Precio', monto: p.precio };
}

const FORMATO_MXN = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });

export function formatoMoneda(monto: number): string {
  return FORMATO_MXN.format(monto);
}

/** Iniciales (máx. 2) para el avatar de respaldo del logotipo. */
export function inicialesDe(nombre: string | null | undefined): string {
  const palabras = (nombre ?? '').split(/\s+/).map((w) => w.replace(/[^\p{L}\p{N}]/gu, '')).filter(Boolean);
  if (palabras.length === 0) return '?';
  const letras = palabras.length === 1 ? [...palabras[0]].slice(0, 2) : [[...palabras[0]][0], [...palabras[1]][0]];
  return letras.join('').toLocaleUpperCase('es-MX');
}

/** Minúsculas y sin acentos, para comparar texto. */
export function normalizarTexto(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es-MX').trim();
}

export function categoriaDe(p: Pick<Producto, 'categoria'>): string {
  const nombre = p.categoria?.trim();
  return nombre ? nombre : CATEGORIA_SIN_NOMBRE;
}

export function claveProducto(p: Pick<Producto, 'idServicio' | 'idProducto'>): string {
  return `${p.idServicio}-${p.idProducto}`;
}
