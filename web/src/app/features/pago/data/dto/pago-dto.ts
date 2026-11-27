import {
  CuentaCliente,
  ESTATUS_CONOCIDOS,
  EstatusPago,
  MOTIVOS_CONOCIDOS,
  MotivoPago,
  ResultadoPago,
} from '../../domain/models/pago';

export interface CuentaDto {
  numCuenta?: string | null;
  numCuentaEnmascarada?: string | null;
  saldo?: number | null;
  estatus?: string | null;
}

export interface PagoServicioRequestDto {
  numCuenta: string;
  idSer: number;
  idProd: number;
  monto: number;
  esReferencia: boolean;
  telefono?: string;
  reference?: string;
  idValidacion?: string;
  twoFA: string;
}

export interface EstadoPagoRequestDto {
  upc?: string;
  idempotencyKey?: string;
}

/** Todos los campos son opcionales: el mapeo tolera nulos y omisiones. */
export interface PagoServicioResponseDto {
  numCuenta?: string | null;
  upc?: string | null;
  estatus?: string | null;
  motivo?: string | null;
  monto?: number | null;
  saldoRestante?: number | null;
  folio?: string | null;
  idTx?: string | null;
  identificadorOperacion?: string | null;
  comision?: number | null;
  pinServicio?: string | null;
  leyenda?: string | null;
  servicio?: string | null;
  producto?: string | null;
  referenciaEnmascarada?: string | null;
  telefonoEnmascarado?: string | null;
  codigoRespuesta?: string | null;
  mensajeRespuesta?: string | null;
  fechaTransaccion?: string | null;
  idServicio?: number | null;
  idProducto?: number | null;
  logoUrl?: string | null;
}

const texto = (v: unknown): string | null => (typeof v === 'string' && v.trim() !== '' ? v : null);
const numero = (v: unknown): number | null => (typeof v === 'number' && Number.isFinite(v) ? v : null);

function esObjeto(v: unknown): boolean {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

/** Estatus no previsto => `DESCONOCIDO` (nunca se interpreta como éxito). */
export function aEstatus(valor: unknown): EstatusPago {
  return ESTATUS_CONOCIDOS.find((e) => e === valor) ?? 'DESCONOCIDO';
}

export function aMotivo(valor: unknown): MotivoPago | null {
  if (valor === null || valor === undefined) return null;
  return MOTIVOS_CONOCIDOS.find((m) => m === valor) ?? 'DESCONOCIDO';
}

/** Mapea cuentas; descarta las que no sean ACTIVAS o carezcan de número o saldo. */
export function aCuentas(dtos: unknown): CuentaCliente[] {
  if (!Array.isArray(dtos)) return [];
  const cuentas: CuentaCliente[] = [];
  for (const d of dtos as CuentaDto[]) {
    if (!esObjeto(d)) continue;
    const num = texto(d.numCuenta);
    const saldo = numero(d.saldo);
    if (num === null || saldo === null) continue;
    if (d.estatus != null && d.estatus !== 'ACTIVA') continue;
    cuentas.push({ numCuenta: num, numCuentaEnmascarada: texto(d.numCuentaEnmascarada) ?? `********${num.slice(-4)}`, saldo });
  }
  return cuentas;
}

/** Mapea la respuesta de pago; null si no es un objeto. Ignora `codigoRespuesta` y `mensajeRespuesta`. */
export function aPago(dto: unknown): ResultadoPago | null {
  if (!esObjeto(dto)) return null;
  const d = dto as PagoServicioResponseDto;
  return {
    numCuentaEnmascarada: texto(d.numCuenta),
    upc: texto(d.upc),
    estatus: aEstatus(d.estatus),
    motivo: aMotivo(d.motivo),
    monto: numero(d.monto),
    saldoRestante: numero(d.saldoRestante),
    folio: texto(d.folio),
    idTx: texto(d.idTx),
    identificadorOperacion: texto(d.identificadorOperacion),
    pinServicio: texto(d.pinServicio),
    leyenda: texto(d.leyenda),
    servicio: texto(d.servicio),
    producto: texto(d.producto),
    referenciaEnmascarada: texto(d.referenciaEnmascarada),
    telefonoEnmascarado: texto(d.telefonoEnmascarado),
    fechaTransaccion: texto(d.fechaTransaccion),
    idServicio: numero(d.idServicio),
    idProducto: numero(d.idProducto),
    logoUrl: texto(d.logoUrl),
  };
}

/** Lee `motivo`, `intentosRestantes` del `detalle` de un fallo. */
export function leerDetalle(detalle: unknown): { motivo: MotivoPago | null; intentosRestantes: number | null } {
  if (!esObjeto(detalle)) return { motivo: null, intentosRestantes: null };
  const d = detalle as Record<string, unknown>;
  return { motivo: aMotivo(d['motivo']), intentosRestantes: numero(d['intentosRestantes']) };
}
