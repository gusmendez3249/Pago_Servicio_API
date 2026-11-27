import { HttpEventType, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { SessionStore } from '../session/session-store';

/**
 * Agrega `Authorization: Bearer` solo a peticiones al backend propio y solo si
 * hay sesión. Cada respuesta exitosa reinicia el temporizador de inactividad.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const baseUrl = inject(API_BASE_URL);
  const sesion = inject(SessionStore);
  const token = sesion.token();

  if (!req.url.startsWith(baseUrl) || token === null) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })).pipe(
    tap((evento) => {
      if (evento.type === HttpEventType.Response && evento.ok) sesion.registrarActividad();
    }),
  );
};
