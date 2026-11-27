import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, TimeoutError, firstValueFrom, timeout } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { CampoError, Envoltorio, Failure, Result, TipoFallo } from './result';

export const TIMEOUT_POR_DEFECTO_MS = 15_000;
export const TIMEOUT_PAGO_MS = 75_000;

export interface OpcionesPeticion {
  /** Timeout en ms (por defecto 15 s). */
  timeoutMs?: number;
  headers?: Record<string, string>;
  params?: Record<string, string | number | boolean>;
}

function esEnvoltorio(valor: unknown): valor is Envoltorio<unknown> {
  return (
    typeof valor === 'object' &&
    valor !== null &&
    typeof (valor as Envoltorio<unknown>).codigo === 'number' &&
    'mensaje' in valor
  );
}

function tipoPorEstado(status: number): TipoFallo {
  switch (status) {
    case 0:
      return 'red';
    case 400:
      return 'validacion';
    case 401:
      return 'no-autenticado';
    case 403:
      return 'prohibido';
    case 404:
      return 'no-encontrado';
    case 423:
      return 'bloqueado';
    case 429:
      return 'demasiados-intentos';
    default:
      return status >= 500 ? 'servidor' : 'desconocido';
  }
}

function leerRetryAfter(cabecera: string | null): number | undefined {
  if (cabecera === null) return undefined;
  const segundos = Number.parseInt(cabecera, 10);
  return Number.isFinite(segundos) && segundos >= 0 ? segundos : undefined;
}

function esListaDeCampos(data: unknown): data is CampoError[] {
  return (
    Array.isArray(data) &&
    data.every((c) => typeof c === 'object' && c !== null && 'campo' in c && 'mensaje' in c)
  );
}

/** Convierte cualquier error de transporte/HTTP en un `Failure` tipado. */
export function fallaDesdeError(error: unknown): Failure {
  if (error instanceof TimeoutError) {
    return {
      ok: false,
      tipo: 'red',
      httpStatus: 0,
      codigo: 0,
      mensaje: 'El servidor tardó demasiado en responder.',
    };
  }
  if (error instanceof HttpErrorResponse) {
    const tipo = tipoPorEstado(error.status);
    const cuerpo = error.error;
    const envoltorio = esEnvoltorio(cuerpo) ? cuerpo : null;
    const falla: Failure = {
      ok: false,
      tipo,
      httpStatus: error.status,
      codigo: envoltorio?.codigo ?? error.status,
      mensaje:
        envoltorio?.mensaje ??
        (tipo === 'red' ? 'No se pudo conectar con el servidor.' : 'Ocurrió un error inesperado.'),
    };
    if (tipo === 'validacion' && envoltorio && esListaDeCampos(envoltorio.data)) {
      falla.campos = envoltorio.data;
    }
    if (envoltorio && envoltorio.data !== null && envoltorio.data !== undefined) {
      falla.detalle = envoltorio.data;
    }
    if (tipo === 'demasiados-intentos') {
      falla.retryAfterSegundos = leerRetryAfter(error.headers?.get('Retry-After') ?? null);
    }
    return falla;
  }
  return { ok: false, tipo: 'desconocido', httpStatus: 0, codigo: 0, mensaje: 'Ocurrió un error inesperado.' };
}

/** Cliente HTTP del backend: siempre resuelve con `Result`, nunca rechaza. */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  get<T>(ruta: string, opciones: OpcionesPeticion = {}): Promise<Result<T>> {
    return this.ejecutar<T>(
      this.http.get<Envoltorio<T>>(this.url(ruta), { observe: 'response', ...this.opcionesHttp(opciones) }),
      opciones,
    );
  }

  post<T>(ruta: string, cuerpo: unknown = null, opciones: OpcionesPeticion = {}): Promise<Result<T>> {
    return this.ejecutar<T>(
      this.http.post<Envoltorio<T>>(this.url(ruta), cuerpo, {
        observe: 'response',
        ...this.opcionesHttp(opciones),
      }),
      opciones,
    );
  }

  private url(ruta: string): string {
    return `${this.baseUrl}${ruta.startsWith('/') ? '' : '/'}${ruta}`;
  }

  private opcionesHttp(opciones: OpcionesPeticion): { headers?: HttpHeaders; params?: HttpParams } {
    return {
      headers: opciones.headers ? new HttpHeaders(opciones.headers) : undefined,
      params: opciones.params ? new HttpParams({ fromObject: opciones.params }) : undefined,
    };
  }

  private async ejecutar<T>(
    peticion$: Observable<HttpResponse<Envoltorio<T>>>,
    opciones: OpcionesPeticion,
  ): Promise<Result<T>> {
    try {
      const respuesta = await firstValueFrom(
        peticion$.pipe(timeout({ first: opciones.timeoutMs ?? TIMEOUT_POR_DEFECTO_MS })),
      );
      const cuerpo = respuesta.body;
      if (!esEnvoltorio(cuerpo)) {
        return {
          ok: false,
          tipo: 'desconocido',
          httpStatus: respuesta.status,
          codigo: respuesta.status,
          mensaje: 'Respuesta del servidor con formato inesperado.',
        };
      }
      if (cuerpo.codigo !== 0) {
        return {
          ok: false,
          tipo: 'desconocido',
          httpStatus: respuesta.status,
          codigo: cuerpo.codigo,
          mensaje: cuerpo.mensaje,
        };
      }
      return { ok: true, data: cuerpo.data as T, mensaje: cuerpo.mensaje, httpStatus: respuesta.status };
    } catch (error) {
      return fallaDesdeError(error);
    }
  }
}
