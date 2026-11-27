import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../config/app-config';
import { ApiClient } from './api-client';
import { nuevaIdempotencyKey } from './idempotency';
import { Failure } from './result';

describe('ApiClient', () => {
  let api: ApiClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(ApiClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('desempaqueta el envoltorio de éxito', async () => {
    const promesa = api.get<{ a: number }>('/x');
    http.expectOne('http://api.test/x').flush({ codigo: 0, mensaje: 'ok', data: { a: 1 } });
    const r = await promesa;
    expect(r).toEqual({ ok: true, data: { a: 1 }, mensaje: 'ok', httpStatus: 200 });
  });

  it('mapea validación (codigo 1, HTTP 400) con campos', async () => {
    const promesa = api.post('/x', {});
    http.expectOne('http://api.test/x').flush(
      { codigo: 1, mensaje: 'Datos inválidos', data: [{ campo: 'username', mensaje: 'requerido' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    const r = (await promesa) as Failure;
    expect(r.ok).toBe(false);
    expect(r.tipo).toBe('validacion');
    expect(r.codigo).toBe(1);
    expect(r.campos).toEqual([{ campo: 'username', mensaje: 'requerido' }]);
  });

  it('mapea 401 a no-autenticado', async () => {
    const promesa = api.get('/x');
    http.expectOne('http://api.test/x').flush(
      { codigo: 401, mensaje: 'Credenciales inválidas', data: null },
      { status: 401, statusText: 'Unauthorized' },
    );
    const r = (await promesa) as Failure;
    expect(r).toMatchObject({ ok: false, tipo: 'no-autenticado', httpStatus: 401, mensaje: 'Credenciales inválidas' });
  });

  it('mapea 429 con Retry-After', async () => {
    const promesa = api.get('/x');
    http.expectOne('http://api.test/x').flush(
      { codigo: 429, mensaje: 'Muchos intentos', data: null },
      { status: 429, statusText: 'Too Many', headers: { 'Retry-After': '30' } },
    );
    const r = (await promesa) as Failure;
    expect(r.tipo).toBe('demasiados-intentos');
    expect(r.retryAfterSegundos).toBe(30);
  });

  it('mapea un fallo de red (status 0) sin lanzar', async () => {
    const promesa = api.get('/x');
    http.expectOne('http://api.test/x').error(new ProgressEvent('error'), { status: 0 });
    const r = (await promesa) as Failure;
    expect(r).toMatchObject({ ok: false, tipo: 'red', httpStatus: 0 });
  });

  it('aplica timeout configurable y devuelve fallo de red', async () => {
    vi.useFakeTimers();
    try {
      const promesa = api.get('/x', { timeoutMs: 1000 });
      http.expectOne('http://api.test/x');
      await vi.advanceTimersByTimeAsync(1001);
      const r = (await promesa) as Failure;
      expect(r.tipo).toBe('red');
    } finally {
      vi.useRealTimers();
    }
  });

  it('conserva el data de un error de negocio en `detalle`', async () => {
    const promesa = api.post('/x', {});
    http.expectOne('http://api.test/x').flush(
      { codigo: 403, mensaje: 'texto', data: { motivo: 'PIN_INCORRECTO', intentosRestantes: 2 } },
      { status: 403, statusText: 'Forbidden' },
    );
    const r = (await promesa) as Failure;
    expect(r.detalle).toEqual({ motivo: 'PIN_INCORRECTO', intentosRestantes: 2 });
  });

  it('envía headers por llamada y respeta el código 202', async () => {
    const promesa = api.post<{ a: number }>('/x', {}, { headers: { 'Idempotency-Key': 'k1' } });
    const req = http.expectOne('http://api.test/x');
    expect(req.request.headers.get('Idempotency-Key')).toBe('k1');
    req.flush({ codigo: 0, mensaje: 'ok', data: { a: 1 } }, { status: 202, statusText: 'Accepted' });
    expect(await promesa).toMatchObject({ ok: true, httpStatus: 202 });
  });

  it('genera Idempotency-Key distintas', () => {
    expect(nuevaIdempotencyKey()).not.toBe(nuevaIdempotencyKey());
  });
});
