import { Injectable, inject } from '@angular/core';
import { SalidaEstado } from '../../../pago/domain/models/pago';
import { EstatusFiltro, SalidaHistorial, TAMANIO_PAGINA } from '../models/historial';
import { HistorialRepository } from '../repository/historial-repository';

@Injectable({ providedIn: 'root' })
export class ObtenerHistorialUseCase {
  private readonly repositorio = inject(HistorialRepository);

  ejecutar(estatus: EstatusFiltro | null, pagina: number): Promise<SalidaHistorial> {
    return this.repositorio.obtenerPagina(estatus, pagina, TAMANIO_PAGINA);
  }
}

@Injectable({ providedIn: 'root' })
export class ObtenerDetallePagoUseCase {
  private readonly repositorio = inject(HistorialRepository);

  ejecutar(upc: string): Promise<SalidaEstado> {
    return this.repositorio.obtenerDetalle(upc);
  }
}
