import { Injectable, inject } from '@angular/core';
import { Result } from '../../../../core/http/result';
import { SessionStore } from '../../../../core/session/session-store';
import { Credenciales, Sesion } from '../models/sesion';
import { AuthRepository } from '../repository/auth-repository';

@Injectable({ providedIn: 'root' })
export class LoginUseCase {
  private readonly repositorio = inject(AuthRepository);
  private readonly sesion = inject(SessionStore);

  async ejecutar(credenciales: Credenciales): Promise<Result<Sesion>> {
    const resultado = await this.repositorio.login(credenciales);
    if (resultado.ok) {
      const { token, username, nombreCliente, rol } = resultado.data;
      this.sesion.iniciar({ token, username, nombreCliente, rol });
    }
    return resultado;
  }
}
