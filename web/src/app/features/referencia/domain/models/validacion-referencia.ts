/** Resultado de la verificación en el proveedor. */
export type EstadoValidacion = 'VALIDA' | 'INVALIDA';
export type MotivoValidacion = 'REFERENCIA_INVALIDA';
export type SituacionSaldo = 'ADEUDO' | 'SIN_ADEUDO' | 'SALDO_A_FAVOR';

export type Verificacion = 'EN_LINEA' | 'SOLO_FORMATO';
export const VERIFICACIONES: readonly Verificacion[] = ['EN_LINEA', 'SOLO_FORMATO'];

export const ESTADOS_VALIDACION: readonly EstadoValidacion[] = ['VALIDA', 'INVALIDA'];
export const MOTIVOS_VALIDACION: readonly MotivoValidacion[] = ['REFERENCIA_INVALIDA'];
export const SITUACIONES_SALDO: readonly SituacionSaldo[] = ['ADEUDO', 'SIN_ADEUDO', 'SALDO_A_FAVOR'];

export interface ValidacionReferencia {
  estado: EstadoValidacion;
  motivo: MotivoValidacion | null;
  situacion: SituacionSaldo | null;
  saldo: number | null;
  idValidacion: string | null;
  vigenciaSegundos: number | null;
  puedePagar: boolean;
  /** null del backend se trata como EN_LINEA. */
  verificacion: Verificacion;
  montoAPagar: number | null;
  idSer: number;
  idProd: number;
  referencia: string;
  servicio: string;
  producto: string;
}

/** Datos que el Sprint 3 necesita para pagar; solo viven en memoria. */
export interface DatosPago {
  idSer: number;
  idProd: number;
  referencia: string;
  idValidacion: string;
  saldo: number | null;
  situacion: SituacionSaldo | null;
  verificacion: Verificacion;
  montoAPagar: number | null;
  servicio: string;
  producto: string;
  /** Marca de tiempo (ms) en que vence el comprobante. */
  expiraEn: number;
}
