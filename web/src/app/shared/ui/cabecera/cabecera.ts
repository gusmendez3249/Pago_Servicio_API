import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { SessionStore } from '../../../core/session/session-store';
import { Icono } from '../icono/icono';
import { Logotipo } from '../logotipo/logotipo';

/**
 * Navegación principal: Inicio, Mis pagos y Cuenta (cliente) o Recargas (administrador). Desde 600 px va en la cabecera (el nombre del cliente
 * lleva a Cuenta); por debajo, en una barra inferior. Cerrar sesión vive en la pantalla Cuenta.
 */
@Component({
  selector: 'app-cabecera',
  imports: [Icono, Logotipo, RouterLink, RouterLinkActive],
  templateUrl: './cabecera.html',
  styleUrl: './cabecera.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Cabecera {
  protected readonly sesion = inject(SessionStore);
  /** El administrador (EJECUTIVO) solo ve su panel de recargas. */
  protected readonly esAdmin = computed(() => this.sesion.rol() === 'EJECUTIVO');
}
