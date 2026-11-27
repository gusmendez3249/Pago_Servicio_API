import { Routes } from '@angular/router';
import { soloAdminGuard, soloClienteGuard, soloPublicoGuard } from './core/guards/auth.guard';
import { pagoPermitidoGuard } from './features/pago/presentation/guards/pago-permitido.guard';
import { pagoSalidaGuard } from './features/pago/presentation/guards/pago-salida.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [soloPublicoGuard],
    loadComponent: () =>
      import('./features/auth/presentation/pages/login-page').then((m) => m.LoginPage),
    title: 'Iniciar sesión · BanGus',
  },
  {
    path: 'registro',
    canActivate: [soloPublicoGuard],
    loadComponent: () =>
      import('./features/registro/presentation/pages/registro-page').then((m) => m.RegistroPage),
    title: 'Crear cuenta · BanGus',
  },
  {
    path: 'admin',
    canActivate: [soloAdminGuard],
    loadComponent: () => import('./features/admin/presentation/pages/admin-page').then((m) => m.AdminPage),
    title: 'Recargas de saldo · BanGus',
  },
  { path: 'inicio', redirectTo: 'catalogo', pathMatch: 'full' },
  {
    path: 'catalogo',
    canActivate: [soloClienteGuard],
    loadComponent: () =>
      import('./features/catalogo/presentation/pages/catalogo-page').then((m) => m.CatalogoPage),
    title: 'Catálogo · BanGus',
  },
  {
    path: 'catalogo/:idServicio/:idProducto',
    canActivate: [soloClienteGuard],
    loadComponent: () =>
      import('./features/catalogo/presentation/pages/producto-detalle-page').then((m) => m.ProductoDetallePage),
    title: 'Detalle del servicio · BanGus',
  },
  {
    path: 'catalogo/:idServicio/:idProducto/referencia',
    canActivate: [soloClienteGuard],
    loadComponent: () =>
      import('./features/referencia/presentation/pages/referencia-page').then((m) => m.ReferenciaPage),
    title: 'Referencia de pago · BanGus',
  },
  {
    path: 'catalogo/:idServicio/:idProducto/pago',
    canActivate: [soloClienteGuard, pagoPermitidoGuard],
    canDeactivate: [pagoSalidaGuard],
    loadComponent: () => import('./features/pago/presentation/pages/pago-page').then((m) => m.PagoPage),
    title: 'Pago · BanGus',
  },
  {
    path: 'historial',
    canActivate: [soloClienteGuard],
    loadComponent: () =>
      import('./features/historial/presentation/pages/historial-page').then((m) => m.HistorialPage),
    title: 'Mis pagos · BanGus',
  },
  {
    path: 'historial/:upc',
    canActivate: [soloClienteGuard],
    loadComponent: () =>
      import('./features/historial/presentation/pages/detalle-pago-page').then((m) => m.DetallePagoPage),
    title: 'Detalle del pago · BanGus',
  },
  {
    path: 'cuenta',
    canActivate: [soloClienteGuard],
    loadComponent: () => import('./features/cuenta/presentation/pages/cuenta-page').then((m) => m.CuentaPage),
    title: 'Tu cuenta · BanGus',
  },
  { path: '', pathMatch: 'full', redirectTo: 'catalogo' },
  { path: '**', redirectTo: 'catalogo' },
];
