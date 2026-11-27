import { aPago, PagoServicioResponseDto } from '../../../pago/data/dto/pago-dto';
import { ElementoHistorial, EstatusFiltro, PaginaHistorial } from '../../domain/models/historial';

export interface HistorialRequestDto {
  filtros?: { estatus?: EstatusFiltro };
  pagina: number;
  tamanio: number;
}

/** Tolerante a nulos: cualquier campo puede faltar. */
export interface HistorialResponseDto {
  elementos?: PagoServicioResponseDto[] | null;
  pagina?: number | null;
  tamanio?: number | null;
  total?: number | null;
  hayMas?: boolean | null;
}

const entero = (v: unknown, defecto: number): number => (typeof v === 'number' && Number.isFinite(v) ? v : defecto);

/** Mapea la página; descarta elementos que no sean objetos y nunca conserva `pinServicio` en la lista. */
export function aPagina(dto: unknown): PaginaHistorial | null {
  if (typeof dto !== 'object' || dto === null || Array.isArray(dto)) return null;
  const d = dto as HistorialResponseDto;
  const elementos: ElementoHistorial[] = [];
  for (const crudo of Array.isArray(d.elementos) ? d.elementos : []) {
    const pago = aPago(crudo);
    if (pago !== null) elementos.push({ ...pago, pinServicio: null, saldoRestante: null });
  }
  return {
    elementos,
    pagina: entero(d.pagina, 0),
    tamanio: entero(d.tamanio, elementos.length),
    total: entero(d.total, elementos.length),
    hayMas: d.hayMas === true,
  };
}
