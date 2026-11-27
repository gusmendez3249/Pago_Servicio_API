import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../../core/http/api-client';
import { Result } from '../../../core/http/result';

export interface CuentaAdmin {
  numCuentaEnmascarada: string;
  titular: string;
  saldo: number;
  estatus: string;
  recargable: boolean;
}

export interface Recarga {
  folio: string;
  numCuentaEnmascarada: string;
  titular: string;
  monto: number;
  saldoResultante: number;
  fecha: string;
}

export interface PaginaRecargas {
  recargas: Recarga[];
  pagina: number;
  totalPaginas: number;
  totalRecargas: number;
}

/** Panel de administrador: consulta de cuentas y recargas de saldo (solo EJECUTIVO). */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly api = inject(ApiClient);

  consultarCuenta(numeroCuenta: string): Promise<Result<CuentaAdmin>> {
    return this.api.post<CuentaAdmin>('/api/v1/admin/cuentas/consultar', { numeroCuenta });
  }

  recargar(numeroCuenta: string, monto: number, idempotencyKey: string): Promise<Result<Recarga>> {
    return this.api.post<Recarga>(
      '/api/v1/admin/recargas',
      { numeroCuenta, monto },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
  }

  historial(pagina: number): Promise<Result<PaginaRecargas>> {
    return this.api.post<PaginaRecargas>('/api/v1/admin/recargas/historial', { pagina });
  }
}
