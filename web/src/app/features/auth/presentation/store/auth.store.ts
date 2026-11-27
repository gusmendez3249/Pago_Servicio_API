import { Injectable, inject, signal } from '@angular/core';
import { mensajeGenerico } from '../../../../core/error/mensajes-error';
import { Failure } from '../../../../core/http/result';
import { Credenciales } from '../../domain/models/sesion';
import { LoginUseCase } from '../../domain/use-cases/login.use-case';
import { LogoutUseCase } from '../../domain/use-cases/logout.use-case';

export type EstadoLogin = 'inactivo' | 'cargando' | 'exito' | 'error';

export function mensajeLogin(falla: Failure): string {
  switch (falla.tipo) {
    case 'no-autenticado':
      return 'Usuario o contraseña incorrectos.';
    case 'prohibido':
      return 'Tu cuenta está inactiva. Comunícate con tu ejecutivo para reactivarla.';
    case 'bloqueado':
      return 'Tu cuenta está bloqueada. Comunícate con tu ejecutivo para desbloquearla.';
    default:
      return mensajeGenerico(falla);
  }
}

/** Texto amigable por campo: el detalle que envía el backend nunca se muestra en pantalla. */
const MENSAJE_CAMPO: Record<string, string> = {
  username: 'Revisa tu usuario.',
  password: 'Revisa tu contraseña.',
};

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly login = inject(LoginUseCase);
  private readonly logout = inject(LogoutUseCase);

  readonly estado = signal<EstadoLogin>('inactivo');
  readonly mensajeError = signal<string | null>(null);
  readonly erroresCampo = signal<Record<string, string>>({});

  /** Devuelve true si el login fue exitoso. */
  async iniciarSesion(credenciales: Credenciales): Promise<boolean> {
    this.estado.set('cargando');
    this.mensajeError.set(null);
    this.erroresCampo.set({});

    const resultado = await this.login.ejecutar(credenciales);
    if (resultado.ok) {
      this.estado.set('exito');
      return true;
    }

    this.estado.set('error');
    this.mensajeError.set(mensajeLogin(resultado));
    if (resultado.tipo === 'validacion' && resultado.campos?.length) {
      this.erroresCampo.set(
        Object.fromEntries(
          resultado.campos.filter((c) => c.campo in MENSAJE_CAMPO).map((c) => [c.campo, MENSAJE_CAMPO[c.campo]]),
        ),
      );
    }
    return false;
  }

  limpiarErrorCampo(campo: string): void {
    if (!(campo in this.erroresCampo())) return;
    const { [campo]: _omitido, ...resto } = this.erroresCampo();
    this.erroresCampo.set(resto);
  }

  async cerrarSesion(): Promise<void> {
    await this.logout.ejecutar();
    this.reiniciar();
  }

  reiniciar(): void {
    this.estado.set('inactivo');
    this.mensajeError.set(null);
    this.erroresCampo.set({});
  }
}
