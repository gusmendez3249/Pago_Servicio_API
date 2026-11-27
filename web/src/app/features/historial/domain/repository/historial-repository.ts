import { SalidaEstado } from '../../../pago/domain/models/pago';
import { EstatusFiltro, SalidaHistorial } from '../models/historial';

export abstract class HistorialRepository {
  abstract obtenerPagina(estatus: EstatusFiltro | null, pagina: number, tamanio: number): Promise<SalidaHistorial>;
  abstract obtenerDetalle(upc: string): Promise<SalidaEstado>;
}
