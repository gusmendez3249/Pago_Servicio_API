import { Injectable, inject } from '@angular/core';
import { SessionStore } from '../../../../core/session/session-store';
import { AuthRepository } from '../repository/auth-repository';

@Injectable({ providedIn: 'root' })
export class LogoutUseCase {
  private readonly repositorio = inject(AuthRepository);
  private readonly sesion = inject(SessionStore);

  /** Avisa al backend y, pase lo que pase (éxito, 401, red), limpia la sesión local. */
  async ejecutar(): Promise<void> {
    try {
      await this.repositorio.logout();
    } finally {
      this.sesion.cerrar();
    }
  }
}
