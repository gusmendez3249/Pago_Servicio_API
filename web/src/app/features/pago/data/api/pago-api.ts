import { Injectable, inject } from '@angular/core';
import { ApiClient, TIMEOUT_PAGO_MS } from '../../../../core/http/api-client';
import { Result } from '../../../../core/http/result';
import { CuentaDto, EstadoPagoRequestDto, PagoServicioRequestDto, PagoServicioResponseDto } from '../dto/pago-dto';

@Injectable({ providedIn: 'root' })
export class PagoApi {
  private readonly api = inject(ApiClient);

  cuentasMias(): Promise<Result<CuentaDto[]>> {
    return this.api.post<CuentaDto[]>('/api/v1/cuentas/mias', {});
  }

  /** Pago: la llave de idempotencia viaja en el header y el timeout es de 75 s. */
  pagar(cuerpo: PagoServicioRequestDto, idempotencyKey: string): Promise<Result<PagoServicioResponseDto>> {
    return this.api.post<PagoServicioResponseDto>('/api/v1/pagos/servicio', cuerpo, {
      timeoutMs: TIMEOUT_PAGO_MS,
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  estado(cuerpo: EstadoPagoRequestDto): Promise<Result<PagoServicioResponseDto>> {
    return this.api.post<PagoServicioResponseDto>('/api/v1/pagos/estado', cuerpo);
  }
}
