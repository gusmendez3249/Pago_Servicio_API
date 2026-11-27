import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../../../core/http/api-client';
import { Result } from '../../../../core/http/result';
import { HistorialRequestDto, HistorialResponseDto } from '../dto/historial-dto';

/** La lista siempre se pide al backend: nada se cachea en storage ni en el service worker. */
@Injectable({ providedIn: 'root' })
export class HistorialApi {
  private readonly api = inject(ApiClient);

  historial(cuerpo: HistorialRequestDto): Promise<Result<HistorialResponseDto>> {
    return this.api.post<HistorialResponseDto>('/api/v1/pagos/historial', cuerpo);
  }
}
