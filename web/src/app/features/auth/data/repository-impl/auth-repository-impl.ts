import { Injectable, inject } from '@angular/core';
import { Result } from '../../../../core/http/result';
import { Credenciales, Sesion } from '../../domain/models/sesion';
import { AuthRepository } from '../../domain/repository/auth-repository';
import { AuthApi } from '../api/auth-api';
import { aLoginRequestDto, aSesion } from '../dto/auth-dto';

@Injectable()
export class AuthRepositoryImpl extends AuthRepository {
  private readonly api = inject(AuthApi);

  async login(credenciales: Credenciales): Promise<Result<Sesion>> {
    const resultado = await this.api.login(aLoginRequestDto(credenciales));
    return resultado.ok ? { ...resultado, data: aSesion(resultado.data) } : resultado;
  }

  logout(): Promise<Result<null>> {
    return this.api.logout();
  }
}
