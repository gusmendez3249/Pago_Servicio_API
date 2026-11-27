import { Injectable, inject } from '@angular/core';
import { Result } from '../../../../core/http/result';
import { PagoApi } from '../../../pago/data/api/pago-api';
import { interpretarEstado } from '../../../pago/data/mapeo/interpretar-respuesta';
import { SalidaEstado } from '../../../pago/domain/models/pago';
import { EstatusFiltro, SalidaHistorial } from '../../domain/models/historial';
import { HistorialRepository } from '../../domain/repository/historial-repository';
import { HistorialApi } from '../api/historial-api';
import { HistorialResponseDto, aPagina } from '../dto/historial-dto';

export function interpretarHistorial(r: Result<HistorialResponseDto>): SalidaHistorial {
  if (r.ok) {
    const pagina = aPagina(r.data);
    return pagina === null ? { tipo: 'generico' } : { tipo: 'pagina', pagina };
  }
  if (r.httpStatus === 401) return { tipo: 'noAutenticado' };
  return r.httpStatus === 0 && r.tipo === 'red' ? { tipo: 'sinRed' } : { tipo: 'generico' };
}

@Injectable()
export class HistorialRepositoryImpl extends HistorialRepository {
  private readonly api = inject(HistorialApi);
  private readonly pagoApi = inject(PagoApi);

  async obtenerPagina(estatus: EstatusFiltro | null, pagina: number, tamanio: number): Promise<SalidaHistorial> {
    const filtros = estatus === null ? undefined : { estatus };
    return interpretarHistorial(await this.api.historial({ ...(filtros ? { filtros } : {}), pagina, tamanio }));
  }

  async obtenerDetalle(upc: string): Promise<SalidaEstado> {
    return interpretarEstado(await this.pagoApi.estado({ upc }));
  }
}
