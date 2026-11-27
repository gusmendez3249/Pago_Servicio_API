import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../../../core/http/api-client';
import { Result } from '../../../../core/http/result';
import { ProductoDto } from '../dto/producto-dto';

@Injectable({ providedIn: 'root' })
export class CatalogoApi {
  private readonly api = inject(ApiClient);

  /** Público: no requiere token (404 si no hay datos). */
  obtenerProductos(): Promise<Result<ProductoDto[]>> {
    return this.api.get<ProductoDto[]>('/productos');
  }
}
