import { Injectable, inject } from '@angular/core';
import { Result } from '../../../../core/http/result';
import { validarFormatoReferencia } from '../models/formato-referencia';
import { ValidacionReferencia } from '../models/validacion-referencia';
import { ReferenciaRepository } from '../repository/referencia-repository';

@Injectable({ providedIn: 'root' })
export class ValidarReferenciaUseCase {
  private readonly repositorio = inject(ReferenciaRepository);

  /** Revalida el formato (defensa) y consulta; la referencia viaja recortada. */
  async ejecutar(idSer: number, idProd: number, texto: string): Promise<Result<ValidacionReferencia>> {
    const formato = validarFormatoReferencia(texto);
    if (!formato.valida) {
      return { ok: false, tipo: 'validacion', httpStatus: 0, codigo: 1, mensaje: '' };
    }
    return this.repositorio.validar(idSer, idProd, formato.referencia);
  }
}
