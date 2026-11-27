import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, UrlTree, convertToParamMap, provideRouter } from '@angular/router';
import { Producto } from '../../../catalogo/domain/models/producto';
import { CatalogoStore } from '../../../catalogo/presentation/store/catalogo.store';
import { ValidacionReferenciaStore } from '../../../referencia/presentation/store/validacion-referencia.store';
import { DatosPago } from '../../../referencia/domain/models/validacion-referencia';
import { pagoPermitidoGuard } from './pago-permitido.guard';

function prod(p: Partial<Producto>): Producto {
  return {
    servicio: 'S', producto: 'P', idServicio: 1, idProducto: 2, idCatTipoServicio: null, tipoFront: null, hasDigitoVerificador: false,
    precio: null, showAyuda: false, tipoReferencia: null, legend: null, logoUrl: null, ayudaUrl: null, categoria: null, ...p,
  };
}

describe('pagoPermitidoGuard', () => {
  let producto: Producto | undefined;
  let datos: DatosPago | null;

  beforeEach(() => {
    datos = null;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: CatalogoStore, useValue: { cargar: () => Promise.resolve(), buscarProducto: () => producto } },
        { provide: ValidacionReferenciaStore, useValue: { datosPago: () => datos } },
      ],
    });
  });

  const ejecutar = () => {
    const ruta = { paramMap: convertToParamMap({ idServicio: '1', idProducto: '2' }) } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => pagoPermitidoGuard(ruta, {} as never)) as Promise<boolean | UrlTree>;
  };

  it('tipoFront 1 con precio > 0 pasa sin validación', async () => {
    producto = prod({ tipoFront: 1, precio: 50 });
    expect(await ejecutar()).toBe(true);
  });

  it('tipoFront 1 sin precio o con precio 0 vuelve al detalle', async () => {
    producto = prod({ tipoFront: 1, precio: 0 });
    expect(((await ejecutar()) as UrlTree).toString()).toBe('/catalogo/1/2');
    producto = prod({ tipoFront: 1, precio: null });
    expect(((await ejecutar()) as UrlTree).toString()).toBe('/catalogo/1/2');
  });

  it('tipoFront 2 exige validación vigente del mismo producto', async () => {
    producto = prod({ tipoFront: 2 });
    expect(((await ejecutar()) as UrlTree).toString()).toBe('/catalogo/1/2/referencia');
    datos = { idSer: 1, idProd: 2, referencia: 'A', idValidacion: 'u', saldo: 1, situacion: 'ADEUDO', verificacion: 'EN_LINEA', montoAPagar: 1, servicio: 'S', producto: 'P', expiraEn: Date.now() + 60_000 };
    expect(await ejecutar()).toBe(true);
    datos = { ...datos, expiraEn: Date.now() - 1 };
    expect(await ejecutar()).toBeInstanceOf(UrlTree);
  });

  it('otros tipos o producto inexistente vuelven al detalle', async () => {
    producto = prod({ tipoFront: 5, precio: 10 });
    expect(((await ejecutar()) as UrlTree).toString()).toBe('/catalogo/1/2');
    producto = undefined;
    expect(await ejecutar()).toBeInstanceOf(UrlTree);
  });
});
