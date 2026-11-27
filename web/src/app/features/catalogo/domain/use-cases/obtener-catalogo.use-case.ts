import { Injectable, inject } from '@angular/core';
import { Result } from '../../../../core/http/result';
import { Producto } from '../models/producto';
import { CatalogoRepository } from '../repository/catalogo-repository';

@Injectable({ providedIn: 'root' })
export class ObtenerCatalogoUseCase {
  private readonly repositorio = inject(CatalogoRepository);

  /** El 404 del backend (sin productos) no es un error: se devuelve una lista vacía. */
  async ejecutar(): Promise<Result<Producto[]>> {
    const resultado = await this.repositorio.obtenerProductos();
    if (!resultado.ok && resultado.tipo === 'no-encontrado') {
      return { ok: true, data: [], mensaje: '', httpStatus: resultado.httpStatus };
    }
    return resultado;
  }
}
