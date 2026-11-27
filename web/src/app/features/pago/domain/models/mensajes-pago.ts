import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { ErrorImporte, ErrorTelefono } from './pago-reglas';
import { MotivoRechazo } from './pago';

/**
 * Textos de la pantalla de pago. Regla del proyecto: se escriben aquí; nunca se muestra texto, código ni
 * detalle que venga del backend o del proveedor.
 */
export interface TextoResultado {
  titulo: string;
  cuerpo: string;
}

export const TEXTO_NO_CARGO = 'No se hizo ningún cargo a tu cuenta.';

export function textoExitoso(servicio: string, total: number, folio: string | null): TextoResultado {
  const base = `Tu pago de ${servicio} por ${formatoMoneda(total)} se aplicó correctamente.`;
  return { titulo: '¡Pago realizado!', cuerpo: folio ? `${base} Folio: ${folio}.` : base };
}

export function textoPendiente(upc: string | null): TextoResultado {
  const base =
    'Aún no recibimos la confirmación de la operación. El importe está apartado de tu cuenta. No vuelvas a pagar: si no se aplica, te lo devolvemos.';
  return {
    titulo: 'Estamos confirmando tu pago',
    cuerpo: upc ? `${base} Número de operación: ${upc}.` : base,
  };
}

export const TEXTO_SIN_CONEXION: TextoResultado = {
  titulo: 'Sin conexión',
  cuerpo:
    'No pudimos comunicarnos. Revisa tu internet. Si ya confirmaste el pago, no lo repitas: lo estamos verificando.',
};

export const TEXTO_NO_LLEGO: TextoResultado = {
  titulo: 'No recibimos tu pago',
  cuerpo: 'Tu pago no llegó a procesarse. Puedes reintentarlo: no se hará un cargo doble.',
};

export const TITULO_RECHAZADO = 'No se realizó el pago';
export const TITULO_ERROR_GESTOPAGO = 'Error de Gesto Pago';

/** Título del rechazo: un error propio de Gesto Pago se nombra como tal. */
export function tituloDeRechazo(motivo: MotivoRechazo): string {
  return motivo === 'ERROR_GESTOPAGO' ? TITULO_ERROR_GESTOPAGO : TITULO_RECHAZADO;
}

export function textoRechazo(motivo: MotivoRechazo): { cuerpo: string; accion: string } {
  switch (motivo) {
    case 'REFERENCIA_INVALIDA':
      return { cuerpo: 'No se reconoció la referencia. Revísala en tu recibo.', accion: 'Validar otra referencia' };
    case 'RECHAZADO_PROVEEDOR':
      return { cuerpo: 'La operación fue rechazada.', accion: 'Intentar de nuevo' };
    case 'ERROR_GESTOPAGO':
      return {
        cuerpo: 'Gesto Pago, el proveedor del servicio, tuvo un error y no pudo procesar tu pago. Inténtalo más tarde.',
        accion: 'Entendido',
      };
    default:
      return { cuerpo: 'El servicio no está disponible por ahora. Inténtalo más tarde.', accion: 'Entendido' };
  }
}

export function textoPinIncorrecto(intentos: number | null): string {
  if (intentos === null || intentos < 1) return 'PIN incorrecto. Inténtalo de nuevo.';
  return intentos === 1 ? 'PIN incorrecto. Te queda 1 intento.' : `PIN incorrecto. Te quedan ${intentos} intentos.`;
}

export const TEXTO_PIN_BLOQUEADO: TextoResultado = {
  titulo: 'PIN bloqueado',
  cuerpo: 'Por seguridad bloqueamos tu PIN por 15 minutos. No se hizo ningún cargo.',
};

export function textoSaldoInsuficiente(saldo: number, total: number): TextoResultado {
  return {
    titulo: 'Saldo insuficiente',
    cuerpo: `Tu saldo disponible (${formatoMoneda(saldo)}) no cubre el total (${formatoMoneda(total)}).`,
  };
}

export const TEXTO_VALIDACION_EXPIRADA: TextoResultado = {
  titulo: 'La verificación expiró',
  cuerpo: 'Por seguridad, valida tu referencia otra vez para continuar.',
};

export const TEXTO_SIN_CUENTA: TextoResultado = {
  titulo: 'No encontramos una cuenta activa',
  cuerpo: 'No es posible pagar por ahora.',
};

export const TEXTO_GENERICO: TextoResultado = {
  titulo: 'Algo salió mal',
  cuerpo: 'No pudimos procesar tu solicitud. Inténtalo de nuevo.',
};

export function textoErrorTelefono(error: ErrorTelefono): string {
  return error === 'formato' ? 'Ingresa un número de 10 dígitos.' : 'Los números no coinciden.';
}

export function textoErrorImporte(error: ErrorImporte): string {
  return error === 'vacio'
    ? 'Ingresa el importe de tu recibo.'
    : 'El importe debe estar entre $1.00 y $10,000.00.';
}

export const ETIQUETA_ESTATUS = { CONFIRMADA: 'Exitoso', RECHAZADA: 'Rechazado', INDETERMINADA: 'Pendiente', PENDIENTE: 'Pendiente', DESCONOCIDO: 'Pendiente' } as const;
