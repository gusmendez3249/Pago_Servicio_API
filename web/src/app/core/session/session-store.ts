import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

export type Rol = 'CLIENTE' | 'EJECUTIVO';

export interface DatosSesion {
  token: string;
  username: string;
  nombreCliente: string;
  rol: Rol;
}

/** El token vence en el servidor tras 5 min sin actividad. */
export const INACTIVIDAD_MS = 5 * 60 * 1000;
export const AVISO_INACTIVIDAD = 'Tu sesión expiró por inactividad';
export const AVISO_CUENTA_INACTIVA = 'Tu cuenta está inactiva. Comunícate con tu ejecutivo para reactivarla.';
export const AVISO_SESION_INVALIDA = 'Tu sesión ya no es válida. Inicia sesión de nuevo.';

/**
 * Estado de sesión. El token vive SOLO en memoria (signals): nunca se escribe en
 * localStorage, sessionStorage, IndexedDB ni cookies.
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly router = inject(Router);
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  private readonly _sesion = signal<DatosSesion | null>(null);

  readonly sesion = this._sesion.asReadonly();
  readonly haySesion = computed(() => this._sesion() !== null);
  readonly token = computed(() => this._sesion()?.token ?? null);
  readonly username = computed(() => this._sesion()?.username ?? null);
  readonly nombreCliente = computed(() => this._sesion()?.nombreCliente ?? null);
  readonly rol = computed(() => this._sesion()?.rol ?? null);

  /** Aviso a mostrar en /login tras un cierre no iniciado por el usuario. */
  readonly aviso = signal<string | null>(null);

  iniciar(datos: DatosSesion): void {
    this.aviso.set(null);
    this._sesion.set(datos);
    this.armarTemporizador();
  }

  /** Debe llamarse tras cada petición autenticada exitosa (reinicia los 5 min). */
  registrarActividad(): void {
    if (this._sesion() !== null) this.armarTemporizador();
  }

  /** Limpia la sesión local (no llama al backend). */
  cerrar(): void {
    this.detenerTemporizador();
    this._sesion.set(null);
  }

  /** Cierre local forzado (p. ej. 401) con aviso y redirección a /login. */
  expulsar(aviso: string): void {
    this.cerrar();
    this.aviso.set(aviso);
    void this.router.navigate(['/login']);
  }

  private armarTemporizador(): void {
    this.detenerTemporizador();
    this.temporizador = setTimeout(() => this.expulsar(AVISO_INACTIVIDAD), INACTIVIDAD_MS);
  }

  private detenerTemporizador(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
