import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { inicioDe } from '../../../../core/guards/auth.guard';
import { SessionStore } from '../../../../core/session/session-store';
import { validadoresPassword, validadoresUsuario } from '../components/validadores-login';
import { Banner } from '../../../../shared/ui/banner/banner';
import { Icono } from '../../../../shared/ui/icono/icono';
import { Logotipo } from '../../../../shared/ui/logotipo/logotipo';
import { AuthStore } from '../store/auth.store';

@Component({
  selector: 'app-login-page',
  imports: [Banner, ReactiveFormsModule, Icono, Logotipo, RouterLink],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  protected readonly store = inject(AuthStore);
  protected readonly sesion = inject(SessionStore);
  private readonly router = inject(Router);

  /** Mostrar u ocultar la contraseña (solo presentación). */
  protected readonly verPassword = signal(false);

  protected readonly formulario = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: validadoresUsuario }),
    password: new FormControl('', { nonNullable: true, validators: validadoresPassword }),
  });

  protected get username() {
    return this.formulario.controls.username;
  }

  protected get password() {
    return this.formulario.controls.password;
  }

  protected mensajeUsuario(): string | null {
    const rechazado = this.store.erroresCampo()['username'];
    if (rechazado) return rechazado;
    if (!this.username.touched || !this.username.errors) return null;
    const e = this.username.errors;
    if (e['required']) return 'Ingresa tu usuario.';
    if (e['minlength']) return 'El usuario debe tener al menos 3 caracteres.';
    if (e['maxlength']) return 'El usuario no puede superar 50 caracteres.';
    return 'Usa solo letras, números, punto, guion y guion bajo.';
  }

  protected mensajePassword(): string | null {
    const rechazado = this.store.erroresCampo()['password'];
    if (rechazado) return rechazado;
    if (!this.password.touched || !this.password.errors) return null;
    const e = this.password.errors;
    if (e['required']) return 'Ingresa tu contraseña.';
    if (e['minlength']) return 'La contraseña debe tener al menos 8 caracteres.';
    return 'La contraseña no puede superar 72 caracteres.';
  }

  protected async enviar(): Promise<void> {
    if (this.store.estado() === 'cargando') return;
    this.sesion.aviso.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    const exito = await this.store.iniciarSesion(this.formulario.getRawValue());
    this.password.reset('');
    if (exito) {
      await this.router.navigate([inicioDe(this.sesion.rol())]);
    }
  }
}
