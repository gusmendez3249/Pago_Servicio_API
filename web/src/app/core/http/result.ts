/** Error de validación por campo (`codigo` 1, HTTP 400). */
export interface CampoError {
  campo: string;
  mensaje: string;
}

/** Envoltorio estándar de todas las respuestas del backend. */
export interface Envoltorio<T> {
  codigo: number;
  mensaje: string;
  data: T | null;
}

export interface Ok<T> {
  readonly ok: true;
  data: T;
  mensaje: string;
  httpStatus: number;
}

export type TipoFallo =
  | 'validacion'
  | 'no-autenticado'
  | 'prohibido'
  | 'no-encontrado'
  | 'bloqueado'
  | 'demasiados-intentos'
  | 'servidor'
  | 'red'
  | 'desconocido';

export interface Failure {
  readonly ok: false;
  tipo: TipoFallo;
  httpStatus: number;
  codigo: number;
  mensaje: string;
  campos?: CampoError[];
  /** `data` del envoltorio de error (motivo, intentosRestantes, respuesta de pago…). Solo para lógica; nunca se muestra. */
  detalle?: unknown;
  retryAfterSegundos?: number;
}

/** Resultado de toda llamada al API: nunca se lanza una excepción. */
export type Result<T> = Ok<T> | Failure;
