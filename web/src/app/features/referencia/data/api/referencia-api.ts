import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../../../core/http/api-client';
import { Result } from '../../../../core/http/result';
import { ValidacionReferenciaDto, ValidarReferenciaRequestDto } from '../dto/validacion-referencia-dto';

/** El proveedor puede tardar hasta 15 s; el cliente espera un poco más. */
const TIMEOUT_VALIDACION_MS = 30_000;

@Injectable({ providedIn: 'root' })
export class ReferenciaApi {
  private readonly api = inject(ApiClient);

  validar(cuerpo: ValidarReferenciaRequestDto): Promise<Result<ValidacionReferenciaDto>> {
    return this.api.post<ValidacionReferenciaDto>('/api/v1/pagos/referencia/validar', cuerpo, {
      timeoutMs: TIMEOUT_VALIDACION_MS,
    });
  }
}
