import { EstatusPago } from '../../../pago/domain/models/pago';
import { ElementoHistorial, EstadoVisual, EstatusFiltro, FiltroEstado } from './historial';

/** CONFIRMADA es éxito, RECHAZADA es rechazo; INDETERMINADA, PENDIENTE y cualquier valor desconocido son pendientes. */
export function estadoVisualDe(estatus: EstatusPago): EstadoVisual {
  switch (estatus) {
    case 'CONFIRMADA':
      return 'exitoso';
    case 'RECHAZADA':
      return 'rechazado';
    default:
      return 'pendiente';
  }
}

export function etiquetaEstado(estado: EstadoVisual): string {
  switch (estado) {
    case 'exitoso':
      return 'Exitoso';
    case 'rechazado':
      return 'Rechazado';
    default:
      return 'Pendiente';
  }
}

/**
 * Estatus que se piden al backend por cada pestaña. "Pendientes" son dos estatus distintos: se piden por
 * separado (el backend filtra por uno solo) y el store combina los resultados ordenados por fecha. Es más simple
 * y respeta la paginación mejor que pedir todo y filtrar en el cliente, donde una página podría salir vacía.
 */
export function estatusDeFiltro(filtro: FiltroEstado): (EstatusFiltro | null)[] {
  switch (filtro) {
    case 'exitosos':
      return ['CONFIRMADA'];
    case 'rechazados':
      return ['RECHAZADA'];
    case 'pendientes':
      return ['PENDIENTE', 'INDETERMINADA'];
    default:
      return [null];
  }
}

/** Etiqueta del identificador: "Folio" si hay folio; si no, "Número de operación". */
export function etiquetaIdentificador(e: Pick<ElementoHistorial, 'folio'>): string {
  return e.folio ? 'Folio' : 'Número de operación';
}

export function identificadorDe(e: Pick<ElementoHistorial, 'folio' | 'identificadorOperacion' | 'idTx' | 'upc'>): string | null {
  return e.folio ?? e.identificadorOperacion ?? e.idTx ?? e.upc;
}

const FORMATO_PARTES = new Intl.DateTimeFormat('es-MX', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** "13 nov 2026, 19:39" en hora local; null si no hay fecha; el texto original si no se puede interpretar. */
export function formatoFechaHora(crudo: string | null | undefined): string | null {
  if (!crudo) return null;
  const fecha = new Date(crudo);
  if (Number.isNaN(fecha.getTime())) return crudo;
  const partes = Object.fromEntries(FORMATO_PARTES.formatToParts(fecha).map((p) => [p.type, p.value]));
  const mes = String(partes['month']).replace('.', '');
  return `${partes['day']} ${mes} ${partes['year']}, ${partes['hour']}:${partes['minute']}`;
}

/** Referencia o teléfono enmascarado, lo que haya. */
export function destinoDe(e: Pick<ElementoHistorial, 'referenciaEnmascarada' | 'telefonoEnmascarado'>): string | null {
  return e.telefonoEnmascarado ?? e.referenciaEnmascarada;
}
