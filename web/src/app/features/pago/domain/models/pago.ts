/** Estatus del pago; un valor que el cliente no conoce se trata como `DESCONOCIDO` (nunca como éxito). */
export type EstatusPago = 'CONFIRMADA' | 'RECHAZADA' | 'INDETERMINADA' | 'PENDIENTE' | 'DESCONOCIDO';
export const ESTATUS_CONOCIDOS: readonly EstatusPago[] = ['CONFIRMADA', 'RECHAZADA', 'INDETERMINADA', 'PENDIENTE'];

export type MotivoPago =
  | 'REFERENCIA_INVALIDA'
  | 'SERVICIO_NO_DISPONIBLE'
  | 'RECHAZADO_PROVEEDOR'
  | 'ERROR_GESTOPAGO'
  | 'PROVEEDOR_NO_DISPONIBLE'
  | 'SALDO_INSUFICIENTE_CUENTA'
  | 'PIN_INCORRECTO'
  | 'PIN_BLOQUEADO'
  | 'VALIDACION_REQUERIDA'
  | 'DESCONOCIDO';
export const MOTIVOS_CONOCIDOS: readonly MotivoPago[] = [
  'REFERENCIA_INVALIDA',
  'SERVICIO_NO_DISPONIBLE',
  'RECHAZADO_PROVEEDOR',
  'ERROR_GESTOPAGO',
  'PROVEEDOR_NO_DISPONIBLE',
  'SALDO_INSUFICIENTE_CUENTA',
  'PIN_INCORRECTO',
  'PIN_BLOQUEADO',
  'VALIDACION_REQUERIDA',
];

/** Motivos de un pago rechazado que la pantalla distingue. */
export type MotivoRechazo =
  | 'REFERENCIA_INVALIDA'
  | 'RECHAZADO_PROVEEDOR'
  | 'SERVICIO_NO_DISPONIBLE'
  | 'ERROR_GESTOPAGO'
  | 'PROVEEDOR_NO_DISPONIBLE';

/** Cuenta del cliente. El número completo solo vive en memoria y jamás se muestra. */
export interface CuentaCliente {
  numCuenta: string;
  numCuentaEnmascarada: string;
  saldo: number;
}

/** Respuesta de pago (200, 202, 422 y consulta de estado). No incluye ningún texto del servidor. */
export interface ResultadoPago {
  numCuentaEnmascarada: string | null;
  upc: string | null;
  estatus: EstatusPago;
  motivo: MotivoPago | null;
  monto: number | null;
  saldoRestante: number | null;
  folio: string | null;
  idTx: string | null;
  identificadorOperacion: string | null;
  pinServicio: string | null;
  leyenda: string | null;
  servicio: string | null;
  producto: string | null;
  referenciaEnmascarada: string | null;
  telefonoEnmascarado: string | null;
  fechaTransaccion: string | null;
  /** Presentes desde el Sprint 4 (historial y detalle); pueden faltar. */
  idServicio?: number | null;
  idProducto?: number | null;
  logoUrl?: string | null;
}

/** Solicitud de pago; `twoFA` (PIN) existe solo durante la llamada. */
export interface SolicitudPago {
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

export type ConsultaEstado = { upc: string } | { idempotencyKey: string };

/** Resultado interpretado de intentar pagar (la capa de datos ya tradujo códigos HTTP y motivos). */
export type SalidaPago =
  | { tipo: 'exitoso'; pago: ResultadoPago }
  | { tipo: 'pendiente'; pago: ResultadoPago | null }
  | { tipo: 'rechazado'; motivo: MotivoRechazo; pago: ResultadoPago | null }
  | { tipo: 'pinIncorrecto'; intentosRestantes: number | null }
  | { tipo: 'pinBloqueado' }
  | { tipo: 'validacionRequerida' }
  | { tipo: 'saldoInsuficiente' }
  | { tipo: 'sinRed' }
  | { tipo: 'noAutenticado' }
  | { tipo: 'generico' };

/** Resultado interpretado de consultar el estado de una operación. */
export type SalidaEstado =
  | { tipo: 'pago'; pago: ResultadoPago }
  | { tipo: 'noEncontrado' }
  | { tipo: 'noAutenticado' }
  | { tipo: 'sinRed' }
  | { tipo: 'generico' };

export type SalidaCuentas =
  | { tipo: 'cuentas'; cuentas: CuentaCliente[] }
  | { tipo: 'noAutenticado' }
  | { tipo: 'generico' };

/** Producto sobre el que se paga. */
export interface ProductoPago {
  idSer: number;
  idProd: number;
  servicio: string;
  producto: string;
  logoUrl: string | null;
  tipoFront: number;
  precio: number | null;
}
