import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../../../core/session/session-store';
import { AuthRepository } from '../../../auth/domain/repository/auth-repository';
import { SalidaCuentas } from '../../../pago/domain/models/pago';
import { PagoRepository } from '../../../pago/domain/repository/pago-repository';
import { CuentaPage } from './cuenta-page';

async function pintar(cuentasMias: () => Promise<SalidaCuentas>, logout = vi.fn(async () => ({ ok: true as const, data: null, mensaje: '', httpStatus: 200 }))) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'login', children: [] }]),
      { provide: AuthRepository, useValue: { logout } },
      { provide: PagoRepository, useValue: { cuentasMias } },
    ],
  });
  TestBed.inject(SessionStore).iniciar({ token: 't', username: 'u', nombreCliente: 'Ana López', rol: 'CLIENTE' });
  const f = TestBed.createComponent(CuentaPage);
  for (let i = 0; i < 3; i++) {
    await f.whenStable();
    f.detectChanges();
  }
  return { f, el: f.nativeElement as HTMLElement, logout };
}

describe('CuentaPage', () => {
  afterEach(() => TestBed.inject(SessionStore).cerrar());

  it('muestra el nombre del cliente y sus cuentas con saldo', async () => {
    const { el } = await pintar(async () => ({
      tipo: 'cuentas',
      cuentas: [{ numCuenta: '695429041188', numCuentaEnmascarada: '****1188', saldo: 1250.5 }],
    }));
    expect(el.textContent).toContain('Ana López');
    expect(el.textContent).toContain('Cuenta ****1188');
    expect(el.textContent).toContain('$1,250.50');
    expect(el.textContent).not.toContain('695429041188');
  });

  it('error al cargar cuentas: texto amigable y Reintentar', async () => {
    const { el } = await pintar(async () => ({ tipo: 'generico' }));
    expect(el.textContent).toContain('No pudimos cargar tus cuentas. Inténtalo de nuevo.');
    expect(el.textContent).toContain('Reintentar');
  });

  const boton = (el: HTMLElement, texto: string) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.trim().includes(texto)) as HTMLButtonElement;

  it('cerrar sesión abre un diálogo modal con el botón de confirmar en rojo', async () => {
    const { f, el } = await pintar(async () => ({ tipo: 'cuentas', cuentas: [] }));
    boton(el, 'Cerrar sesión').click();
    f.detectChanges();
    const dialogo = el.querySelector('[role=dialog]');
    expect(dialogo?.getAttribute('aria-modal')).toBe('true');
    expect(dialogo?.textContent).toContain('¿Cerrar sesión?');
    expect(dialogo?.textContent).toContain('Tendrás que volver a iniciar sesión para pagar tus servicios.');
    const confirmar = [...dialogo!.querySelectorAll('button')].find((b) => b.textContent?.includes('Cerrar sesión'))!;
    expect(confirmar.classList).toContain('btn--peligro');
  });

  it('sin confirmar no se cierra la sesión: Cancelar, Escape y clic en el velo solo cierran el diálogo', async () => {
    const { f, el, logout } = await pintar(async () => ({ tipo: 'cuentas', cuentas: [] }));
    const abrir = () => {
      boton(el, 'Cerrar sesión').click();
      f.detectChanges();
    };

    abrir();
    boton(el.querySelector('[role=dialog]') as HTMLElement, 'Cancelar').click();
    f.detectChanges();
    expect(el.querySelector('[role=dialog]')).toBeNull();

    abrir();
    el.querySelector('app-dialogo-confirmacion')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    f.detectChanges();
    expect(el.querySelector('[role=dialog]')).toBeNull();

    abrir();
    (el.querySelector('app-dialogo-confirmacion') as HTMLElement).click();
    f.detectChanges();
    expect(el.querySelector('[role=dialog]')).toBeNull();

    expect(logout).not.toHaveBeenCalled();
  });

  it('confirmar en el diálogo sí cierra la sesión', async () => {
    const { f, el, logout } = await pintar(async () => ({ tipo: 'cuentas', cuentas: [] }));
    boton(el, 'Cerrar sesión').click();
    f.detectChanges();
    const dialogo = el.querySelector('[role=dialog]') as HTMLElement;
    [...dialogo.querySelectorAll('button')].find((b) => b.textContent?.includes('Cerrar sesión'))!.click();
    await f.whenStable();
    expect(logout).toHaveBeenCalledTimes(1);
  });
});
