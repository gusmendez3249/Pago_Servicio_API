import { Injectable, inject } from '@angular/core';
import { Result } from '../../../../core/http/result';
import { ValidacionReferencia } from '../../domain/models/validacion-referencia';
import { ReferenciaRepository } from '../../domain/repository/referencia-repository';
import { ReferenciaApi } from '../api/referencia-api';
import { aValidacion } from '../dto/validacion-referencia-dto';

@Injectable()
export class ReferenciaRepositoryImpl extends ReferenciaRepository {
  private readonly api = inject(ReferenciaApi);

  async validar(idSer: number, idProd: number, referencia: string): Promise<Result<ValidacionReferencia>> {
    const resultado = await this.api.validar({ idSer, idProd, referencia });
    if (!resultado.ok) return resultado;
    const validacion = aValidacion(resultado.data);
    if (validacion === null) {
      return { ok: false, tipo: 'desconocido', httpStatus: resultado.httpStatus, codigo: resultado.httpStatus, mensaje: '' };
    }
    return { ...resultado, data: validacion };
  }
}
