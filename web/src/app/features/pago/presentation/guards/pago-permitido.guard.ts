import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CatalogoStore } from '../../../catalogo/presentation/store/catalogo.store';
import { admitePagoDirecto } from '../../../catalogo/domain/models/producto-reglas';
import { ValidacionReferenciaStore } from '../../../referencia/presentation/store/validacion-referencia.store';

/** Tipos de producto que se pagan: 1 (precio fijo con teléfono) y 2 (referencia). */
const TIPO_REFERENCIA_PAGABLE = 2;

/**
 * Candado hacia el pago. tipoFront 2 exige una validación de referencia vigente del mismo producto; tipoFront 1
 * exige precio mayor a 0 (sin validación). Cualquier otro caso vuelve al detalle del producto.
 */
export const pagoPermitidoGuard: CanActivateFn = async (route) => {
  const catalogo = inject(CatalogoStore);
  const validacion = inject(ValidacionReferenciaStore);
  const router = inject(Router);
  const idServicio = Number(route.paramMap.get('idServicio'));
  const idProducto = Number(route.paramMap.get('idProducto'));
  const detalle = router.createUrlTree(['/catalogo', idServicio, idProducto]);

  await catalogo.cargar();
  const producto = catalogo.buscarProducto(idServicio, idProducto);
  if (producto === undefined) return detalle;

  if (admitePagoDirecto(producto)) return true;

  if (producto.tipoFront === TIPO_REFERENCIA_PAGABLE) {
    const datos = validacion.datosPago();
    const vigente =
      datos !== null && datos.idSer === idServicio && datos.idProd === idProducto && Date.now() < datos.expiraEn;
    return vigente ? true : router.createUrlTree(['/catalogo', idServicio, idProducto, 'referencia']);
  }
  return detalle;
};
