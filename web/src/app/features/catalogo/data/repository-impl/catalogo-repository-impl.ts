import { Injectable, inject } from '@angular/core';
import { Result } from '../../../../core/http/result';
import { Producto } from '../../domain/models/producto';
import { CatalogoRepository } from '../../domain/repository/catalogo-repository';
import { CatalogoApi } from '../api/catalogo-api';
import { aProducto } from '../dto/producto-dto';

@Injectable()
export class CatalogoRepositoryImpl extends CatalogoRepository {
  private readonly api = inject(CatalogoApi);

  async obtenerProductos(): Promise<Result<Producto[]>> {
    const resultado = await this.api.obtenerProductos();
    return resultado.ok ? { ...resultado, data: (resultado.data ?? []).map(aProducto) } : resultado;
  }
}
