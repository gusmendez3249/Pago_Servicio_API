import { Result } from '../../../../core/http/result';
import { ValidacionReferencia } from '../models/validacion-referencia';

export abstract class ReferenciaRepository {
  abstract validar(idSer: number, idProd: number, referencia: string): Promise<Result<ValidacionReferencia>>;
}
