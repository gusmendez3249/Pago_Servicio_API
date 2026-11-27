import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AVISO_CUENTA_INACTIVA, AVISO_SESION_INVALIDA, SessionStore } from '../session/session-store';

/**
 * Ante un 401 en una petición autenticada (la que lleva Authorization, por lo que
 * el login queda fuera): limpia la sesión y navega a /login. No reintenta.
 * Debe registrarse DESPUÉS de `authInterceptor`.
 */
export const noAutorizadoInterceptor: HttpInterceptorFn = (req, next) => {
  const sesion = inject(SessionStore);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && req.headers.has('Authorization')) {
        // En el logout un 401 equivale a éxito: no se muestra aviso.
        if (req.url.endsWith('/auth/logout')) {
          sesion.cerrar();
        } else {
          sesion.expulsar(AVISO_SESION_INVALIDA);
        }
      }
      if (
        error instanceof HttpErrorResponse &&
        error.status === 403 &&
        req.headers.has('Authorization') &&
        esClienteInactivo(error.error)
      ) {
        sesion.expulsar(AVISO_CUENTA_INACTIVA);
      }
      return throwError(() => error);
    }),
  );
};

/** `data.motivo` del envoltorio de error: solo para lógica, nunca se muestra. */
function esClienteInactivo(cuerpo: unknown): boolean {
  const data = (cuerpo as { data?: { motivo?: unknown } } | null)?.data;
  const motivo = typeof data === 'object' && data !== null ? data.motivo : null;
  return motivo === 'CLIENTE_INACTIVO' || motivo === 'CUENTA_INACTIVA';
}
