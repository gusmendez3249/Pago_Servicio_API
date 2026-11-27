import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthRepository } from '../../../auth/domain/repository/auth-repository';
import { SessionStore } from '../../../../core/session/session-store';
import { Producto } from '../../domain/models/producto';
import { CatalogoRepository } from '../../domain/repository/catalogo-repository';
import { CatalogoPage } from './catalogo-page';

function producto(i: number, extra: Partial<Producto> = {}): Producto {
  return {
    servicio: `Servicio ${i}`, producto: `Producto ${i}`, idServicio: i, idProducto: i, idCatTipoServicio: null,
    tipoFront: 1, hasDigitoVerificador: false, precio: 10, showAyuda: false, tipoReferencia: null, legend: null,
    logoUrl: null, ayudaUrl: null, categoria: 'Pago de servicios', ...extra,
  };
}

describe('CatalogoPage (conteo de productos)', () => {
  it('muestra todos los productos, incluso con claves idServicio-idProducto repetidas o nulas', async () => {
    const productos = [
      ...Array.from({ length: 10 }, (_, i) => producto(i + 1)),
      producto(1), // misma clave que otro
      producto(1),
      producto(0, { idServicio: null, idProducto: null }),
      producto(0, { idServicio: null, idProducto: null }),
    ];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthRepository, useValue: {} },
        { provide: CatalogoRepository, useValue: { obtenerProductos: async () => ({ ok: true, data: productos, mensaje: '', httpStatus: 200 }) } },
      ],
    });
    TestBed.inject(SessionStore).iniciar({ token: 't', username: 'u', nombreCliente: 'N', rol: 'CLIENTE' });
    const fixture = TestBed.createComponent(CatalogoPage);
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('ul.lista > li').length).toBe(14);
    expect(el.textContent).toContain('14 servicios encontrados');
    TestBed.inject(SessionStore).cerrar();
  });
});
