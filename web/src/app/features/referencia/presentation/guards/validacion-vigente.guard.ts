import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ValidacionReferenciaStore } from '../store/validacion-referencia.store';

/**
 * Candado hacia el pago: exige una validación vigente (estado `valida`, comprobante no nulo, sin expirar) del
 * mismo producto de la ruta. Si no, vuelve a la captura de referencia de ese producto.
 */
export const validacionVigenteGuard: CanActivateFn = (route) => {
  const store = inject(ValidacionReferenciaStore);
  const router = inject(Router);
  const idServicio = Number(route.paramMap.get('idServicio'));
  const idProducto = Number(route.paramMap.get('idProducto'));

  const datos = store.datosPago();
  const vigente =
    datos !== null && datos.idSer === idServicio && datos.idProd === idProducto && Date.now() < datos.expiraEn;

  return vigente ? true : router.createUrlTree(['/catalogo', idServicio, idProducto, 'referencia']);
};
