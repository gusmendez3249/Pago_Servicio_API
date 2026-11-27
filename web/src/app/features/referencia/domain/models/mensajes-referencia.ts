import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { ErrorFormato } from './formato-referencia';
import { SituacionSaldo } from './validacion-referencia';

/**
 * Textos de la pantalla de referencia. Regla del proyecto: se escriben aquí y nunca se toman de la respuesta
 * del backend ni del proveedor.
 */
export const TEXTO_INVALIDA = 'No encontramos esa referencia. Revísala e inténtalo de nuevo.';
export const TEXTO_ERROR = 'No pudimos verificar tu referencia en este momento. Inténtalo más tarde.';
export const TEXTO_EXPIRADA = 'La verificación expiró. Valida tu referencia de nuevo para continuar.';
export const TEXTO_SOLO_CONSULTA = 'Este servicio solo permite consultar el saldo.';
export const TEXTO_SOLO_FORMATO =
  'No podemos consultar el adeudo de este servicio. Revisa que tu referencia sea correcta antes de continuar.';
export const TEXTO_NADA_POR_PAGAR = 'No hay nada por pagar.';

export function textoTotalAPagar(monto: number): string {
  return `Total a pagar: ${formatoMoneda(monto)}`;
}

export const TEXTO_FORMATO = 'La referencia solo admite letras y números, sin espacios.';
export const TEXTO_VACIA = 'Ingresa tu referencia.';

export function textoErrorFormato(error: ErrorFormato): string {
  return error === 'vacia' ? TEXTO_VACIA : TEXTO_FORMATO;
}

export function textoVerificada(situacion: SituacionSaldo | null, saldo: number | null): string {
  const monto = formatoMoneda(Math.abs(saldo ?? 0));
  switch (situacion) {
    case 'ADEUDO':
      return `Referencia verificada. Tienes un adeudo de ${monto}.`;
    case 'SALDO_A_FAVOR':
      return `Referencia verificada. Tienes un saldo a favor de ${monto}.`;
    default:
      return 'Referencia verificada. No tienes adeudo pendiente.';
  }
}

/** Situación mostrada junto al aviso de solo consulta. */
export function textoSituacion(situacion: SituacionSaldo | null, saldo: number | null): string | null {
  const monto = formatoMoneda(Math.abs(saldo ?? 0));
  switch (situacion) {
    case 'ADEUDO':
      return `Adeudo de ${monto}.`;
    case 'SALDO_A_FAVOR':
      return `Saldo a favor de ${monto}.`;
    case 'SIN_ADEUDO':
      return 'No tienes adeudo pendiente.';
    default:
      return null;
  }
}
