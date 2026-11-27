import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { noAutorizadoInterceptor } from './core/interceptors/no-autorizado.interceptor';
import { AuthRepositoryImpl } from './features/auth/data/repository-impl/auth-repository-impl';
import { AuthRepository } from './features/auth/domain/repository/auth-repository';
import { CatalogoRepositoryImpl } from './features/catalogo/data/repository-impl/catalogo-repository-impl';
import { CatalogoRepository } from './features/catalogo/domain/repository/catalogo-repository';
import { HistorialRepositoryImpl } from './features/historial/data/repository-impl/historial-repository-impl';
import { HistorialRepository } from './features/historial/domain/repository/historial-repository';
import { PagoRepositoryImpl } from './features/pago/data/repository-impl/pago-repository-impl';
import { PagoRepository } from './features/pago/domain/repository/pago-repository';
import { ReferenciaRepositoryImpl } from './features/referencia/data/repository-impl/referencia-repository-impl';
import { ReferenciaRepository } from './features/referencia/domain/repository/referencia-repository';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Orden importa: noAutorizado debe ver la petición ya con Authorization.
    provideHttpClient(withInterceptors([authInterceptor, noAutorizadoInterceptor])),
    { provide: AuthRepository, useClass: AuthRepositoryImpl },
    { provide: CatalogoRepository, useClass: CatalogoRepositoryImpl },
    { provide: ReferenciaRepository, useClass: ReferenciaRepositoryImpl },
    { provide: PagoRepository, useClass: PagoRepositoryImpl },
    { provide: HistorialRepository, useClass: HistorialRepositoryImpl },
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
