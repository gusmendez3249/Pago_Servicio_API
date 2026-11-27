import { Result } from '../../../../core/http/result';
import { Producto } from '../models/producto';

export abstract class CatalogoRepository {
  abstract obtenerProductos(): Promise<Result<Producto[]>>;
}
