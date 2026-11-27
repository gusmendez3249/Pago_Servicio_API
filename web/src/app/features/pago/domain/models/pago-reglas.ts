import { CuentaCliente, MotivoPago, MotivoRechazo, ResultadoPago } from './pago';

export const IMPORTE_MINIMO = 1;
export const IMPORTE_MAXIMO = 10_000;

/** Intervalos de sondeo de un pago pendiente, en ms desde que se entra a `pendiente`. */
export const INTERVALOS_SONDEO_MS: readonly number[] = [10_000, 20_000, 35_000, 50_000, 70_000, 90_000, 120_000];
/** Consultas por llave sin hallazgo (404) tras las cuales se considera que el pago no llegó. */
export const NO_ENCONTRADO_MAXIMO = 3;

export type ErrorTelefono = 'formato' | 'noCoinciden';
export type ErrorImporte = 'vacio' | 'rango';

/** Deja solo dígitos. */
export function soloDigitos(texto: string): string {
  return texto.replace(/\D/g, '');
}

export function validarTelefono(telefono: string, confirmacion: string): ErrorTelefono | null {
  if (!/^\d{10}$/.test(telefono)) return 'formato';
  return telefono === confirmacion ? null : 'noCoinciden';
}

/** Valida y convierte el importe del recibo (1.00–10,000.00, máx. 2 decimales). */
export function validarImporte(texto: string): { ok: true; monto: number } | { ok: false; error: ErrorImporte } {
  const limpio = texto.trim();
  if (limpio === '') return { ok: false, error: 'vacio' };
  if (!/^\d{1,6}(\.\d{1,2})?$/.test(limpio)) return { ok: false, error: 'rango' };
  const monto = Math.round(Number(limpio) * 100) / 100;
  if (monto < IMPORTE_MINIMO || monto > IMPORTE_MAXIMO) return { ok: false, error: 'rango' };
  return { ok: true, monto };
}

export function esPinValido(pin: string): boolean {
  return /^\d{4}$/.test(pin);
}

export interface ResumenPago {
  total: number;
  cuentaEnmascarada: string;
  saldo: number;
  saldoDespues: number;
  saldoInsuficiente: boolean;
}

/** Cálculo del resumen en centavos para evitar errores de coma flotante. */
export function calcularResumen(total: number, cuenta: CuentaCliente): ResumenPago {
  const centavosTotal = Math.round(total * 100);
  const centavosSaldo = Math.round(cuenta.saldo * 100);
  return {
    total,
    cuentaEnmascarada: cuenta.numCuentaEnmascarada,
    saldo: cuenta.saldo,
    saldoDespues: (centavosSaldo - centavosTotal) / 100,
    saldoInsuficiente: centavosSaldo < centavosTotal,
  };
}

/** Motivo que se muestra para un rechazo: cualquier valor no previsto cae en el genérico. */
export function motivoRechazoDe(motivo: MotivoPago | null | undefined): MotivoRechazo {
  switch (motivo) {
    case 'REFERENCIA_INVALIDA':
    case 'SERVICIO_NO_DISPONIBLE':
    case 'ERROR_GESTOPAGO':
    case 'PROVEEDOR_NO_DISPONIBLE':
      return motivo;
    default:
      return 'RECHAZADO_PROVEEDOR';
  }
}

/** Solo un estatus CONFIRMADA recibido del backend es éxito. */
export function esConfirmado(pago: ResultadoPago): boolean {
  return pago.estatus === 'CONFIRMADA';
}
