export const LONGITUD_MAXIMA_REFERENCIA = 100;
export const PATRON_REFERENCIA = /^[A-Za-z0-9]{1,100}$/;

export type ErrorFormato = 'vacia' | 'formato';

export type ResultadoFormato = { valida: true; referencia: string } | { valida: false; error: ErrorFormato };

/** Recorta espacios en los extremos y comprueba el formato (sin longitudes por servicio). */
export function validarFormatoReferencia(texto: string): ResultadoFormato {
  const referencia = texto.trim();
  if (referencia === '') return { valida: false, error: 'vacia' };
  return PATRON_REFERENCIA.test(referencia) ? { valida: true, referencia } : { valida: false, error: 'formato' };
}
