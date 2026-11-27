import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../../../core/session/session-store';
import { AuthRepository } from '../../../auth/domain/repository/auth-repository';
import { Producto } from '../../domain/models/producto';
import { CatalogoRepository } from '../../domain/repository/catalogo-repository';
import { ProductoDetallePage } from './producto-detalle-page';

const producto: Producto = {
  servicio: 'Agua de prueba', producto: 'Pago de agua', idServicio: 133, idProducto: 582, idCatTipoServicio: null,
  tipoFront: 2, hasDigitoVerificador: false, precio: 10, showAyuda: false, tipoReferencia: 'bc', legend: 'Conserva tu comprobante.',
  logoUrl: null, ayudaUrl: null, categoria: 'Pago de servicios',
};

describe('ProductoDetallePage (datos para el cliente)', () => {
  it('no muestra identificadores internos ni el código de tipo de referencia', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'catalogo/:idServicio/:idProducto', component: ProductoDetallePage }]),
        { provide: AuthRepository, useValue: {} },
        { provide: CatalogoRepository, useValue: { obtenerProductos: async () => ({ ok: true, data: [producto], mensaje: '', httpStatus: 200 }) } },
      ],
    });
    TestBed.inject(SessionStore).iniciar({ token: 't', username: 'u', nombreCliente: 'N', rol: 'CLIENTE' });
    const { RouterTestingHarness } = await import('@angular/router/testing');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/catalogo/133/582');
    for (let i = 0; i < 3; i++) {
      await harness.fixture.whenStable();
      harness.detectChanges();
    }
    const ficha = (harness.routeNativeElement as HTMLElement).querySelector('dl.ficha')!;
    const etiquetas = [...ficha.querySelectorAll('dt')].map((dt) => dt.textContent?.trim());
    expect(etiquetas).toEqual(['Categoría', 'Dígito verificador']);
    expect(ficha.textContent).not.toContain('133');
    expect(ficha.textContent).not.toContain('582');
    expect(ficha.textContent).not.toContain('bc');
    TestBed.inject(SessionStore).cerrar();
  });
});
