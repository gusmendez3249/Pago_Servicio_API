import { ResultadoPago } from '../../../pago/domain/models/pago';

/** Operación de la lista: los datos de un pago más lo necesario para pintar el logotipo. `pinServicio` nunca viene aquí. */
export type ElementoHistorial = ResultadoPago;

export interface PaginaHistorial {
  elementos: ElementoHistorial[];
  pagina: number;
  tamanio: number;
  total: number;
  hayMas: boolean;
}

/** Estatus que el backend acepta como filtro. */
export type EstatusFiltro = 'CONFIRMADA' | 'RECHAZADA' | 'INDETERMINADA' | 'PENDIENTE';

/** Pestañas de la lista. */
export type FiltroEstado = 'todos' | 'exitosos' | 'rechazados' | 'pendientes';

/** Cómo se presenta el estado de una operación. */
export type EstadoVisual = 'exitoso' | 'rechazado' | 'pendiente';

export type SalidaHistorial =
  | { tipo: 'pagina'; pagina: PaginaHistorial }
  | { tipo: 'noAutenticado' }
  | { tipo: 'sinRed' }
  | { tipo: 'generico' };

export const TAMANIO_PAGINA = 20;
