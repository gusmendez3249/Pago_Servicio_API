import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { SessionStore } from '../../../../core/session/session-store';
import { PagoStore } from '../store/pago.store';

/** No se puede salir mientras el pago está en proceso (salvo que la sesión ya se haya cerrado). */
export const pagoSalidaGuard: CanDeactivateFn<unknown> = () => {
  const store = inject(PagoStore);
  const sesion = inject(SessionStore);
  return !store.procesando() || !sesion.haySesion();
};
