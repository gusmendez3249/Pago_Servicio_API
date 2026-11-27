import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { INACTIVIDAD_MS, SessionStore } from './session-store';

describe('SessionStore', () => {
  let store: SessionStore;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    store = TestBed.inject(SessionStore);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    store.cerrar();
    vi.useRealTimers();
  });

  const datos = { token: 'tok-secreto', username: 'u', nombreCliente: 'N', rol: 'CLIENTE' as const };

  it('no persiste nada en localStorage ni sessionStorage', () => {
    localStorage.clear();
    sessionStorage.clear();
    store.iniciar(datos);
    expect(store.token()).toBe('tok-secreto');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('tok-secreto');
  });

  it('cerrar() limpia la sesión', () => {
    store.iniciar(datos);
    store.cerrar();
    expect(store.haySesion()).toBe(false);
    expect(store.token()).toBeNull();
  });

  it('expira a los 5 min sin actividad con aviso y redirección', () => {
    vi.useFakeTimers();
    store.iniciar(datos);
    vi.advanceTimersByTime(INACTIVIDAD_MS - 1);
    expect(store.haySesion()).toBe(true);
    vi.advanceTimersByTime(2);
    expect(store.haySesion()).toBe(false);
    expect(store.aviso()).toBe('Tu sesión expiró por inactividad');
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('la actividad reinicia el temporizador', () => {
    vi.useFakeTimers();
    store.iniciar(datos);
    vi.advanceTimersByTime(INACTIVIDAD_MS - 1000);
    store.registrarActividad();
    vi.advanceTimersByTime(INACTIVIDAD_MS - 1000);
    expect(store.haySesion()).toBe(true);
  });
});
