import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionStore } from '../session/session-store';

/** Protege rutas privadas: sin sesión redirige a /login. */
export const authGuard: CanActivateFn = () => {
  const sesion = inject(SessionStore);
  return sesion.haySesion() ? true : inject(Router).createUrlTree(['/login']);
};

/** Pantalla de inicio según el rol: el administrador (EJECUTIVO) entra al panel de recargas. */
export function inicioDe(rol: string | null): string {
  return rol === 'EJECUTIVO' ? '/admin' : '/catalogo';
}

/** Rutas públicas (login, registro): con sesión redirige al inicio de su rol. */
export const soloPublicoGuard: CanActivateFn = () => {
  const sesion = inject(SessionStore);
  return sesion.haySesion() ? inject(Router).createUrlTree([inicioDe(sesion.rol())]) : true;
};

/** Rutas del cliente (catálogo, pagos, cuenta): el administrador no paga servicios y va a su panel. */
export const soloClienteGuard: CanActivateFn = () => {
  const sesion = inject(SessionStore);
  if (!sesion.haySesion()) return inject(Router).createUrlTree(['/login']);
  return sesion.rol() === 'EJECUTIVO' ? inject(Router).createUrlTree(['/admin']) : true;
};

/** Panel de administrador: solo EJECUTIVO. */
export const soloAdminGuard: CanActivateFn = () => {
  const sesion = inject(SessionStore);
  if (!sesion.haySesion()) return inject(Router).createUrlTree(['/login']);
  return sesion.rol() === 'EJECUTIVO' ? true : inject(Router).createUrlTree(['/catalogo']);
};
