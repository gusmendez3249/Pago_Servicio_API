import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { API_BASE_URL } from '../config/app-config';
import { SessionStore } from '../session/session-store';
import { authInterceptor } from './auth.interceptor';
import { noAutorizadoInterceptor } from './no-autorizado.interceptor';

describe('interceptores', () => {
  let cliente: HttpClient;
  let http: HttpTestingController;
  let sesion: SessionStore;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, noAutorizadoInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
      ],
    });
    cliente = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    sesion = TestBed.inject(SessionStore);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    sesion.cerrar();
    http.verify();
  });

  const iniciar = () =>
    sesion.iniciar({ token: 'tok', username: 'u', nombreCliente: 'N', rol: 'CLIENTE' });

  it('agrega Bearer solo al API y solo con sesión', () => {
    cliente.get('http://api.test/a').subscribe();
    expect(http.expectOne('http://api.test/a').request.headers.has('Authorization')).toBe(false);

    iniciar();
    cliente.get('http://api.test/b').subscribe();
    expect(http.expectOne('http://api.test/b').request.headers.get('Authorization')).toBe('Bearer tok');

    cliente.get('http://otro.test/c').subscribe();
    expect(http.expectOne('http://otro.test/c').request.headers.has('Authorization')).toBe(false);
  });

  it('ante 401 autenticado limpia la sesión y navega a /login sin reintentar', () => {
    iniciar();
    let fallo = false;
    cliente.get('http://api.test/b').subscribe({ error: () => (fallo = true) });
    http.expectOne('http://api.test/b').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(fallo).toBe(true);
    expect(sesion.haySesion()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    http.expectNone('http://api.test/b');
  });

  it('ante 403 CLIENTE_INACTIVO o CUENTA_INACTIVA cierra la sesión y avisa en /login', () => {
    for (const motivo of ['CLIENTE_INACTIVO', 'CUENTA_INACTIVA']) {
      iniciar();
      cliente.get('http://api.test/b').subscribe({ error: () => undefined });
      http.expectOne('http://api.test/b').flush({ codigo: 3, mensaje: 'x', data: { motivo } }, { status: 403, statusText: 'Forbidden' });
      expect(sesion.haySesion()).toBe(false);
      expect(sesion.aviso()).toBe('Tu cuenta está inactiva. Comunícate con tu ejecutivo para reactivarla.');
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    }
  });

  it('otro 403 no cierra la sesión', () => {
    iniciar();
    cliente.get('http://api.test/b').subscribe({ error: () => undefined });
    http.expectOne('http://api.test/b').flush({ codigo: 3, mensaje: 'x', data: { motivo: 'NO_TITULAR' } }, { status: 403, statusText: 'Forbidden' });
    expect(sesion.haySesion()).toBe(true);
  });

  it('un 401 sin sesión (login) no navega ni toca la sesión', () => {
    cliente.post('http://api.test/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    http.expectOne('http://api.test/api/v1/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
