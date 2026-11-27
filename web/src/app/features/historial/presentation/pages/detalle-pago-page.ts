import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { Cabecera } from '../../../../shared/ui/cabecera/cabecera';
import { Icono } from '../../../../shared/ui/icono/icono';
import { ComprobantePago } from '../../../pago/presentation/components/comprobante-pago';
import { estadoVisualDe } from '../../domain/models/historial-reglas';
import {
  TEXTO_ERROR_DETALLE,
  TEXTO_NO_ENCONTRADA,
  TEXTO_PENDIENTE_DETALLE,
} from '../../domain/models/mensajes-historial';
import { DetalleHistorialStore } from '../store/detalle-historial.store';

/** Detalle de una operación: el mismo comprobante del flujo de pago. */
@Component({
  selector: 'app-detalle-pago-page',
  imports: [Cabecera, ComprobantePago, Icono, RouterLink],
  providers: [DetalleHistorialStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .detalle__pendiente {
      margin-top: var(--s-4);
    }
  `,
  template: `
    <app-cabecera />
    <main>
      @if (store.estado() === 'listo' && store.pago(); as pago) {
        <app-comprobante-pago [pago]="pago" [conLogo]="true" [volver]="{ ruta: '/historial', texto: 'Mis pagos' }">
          @if (pendiente()) {
            <div class="aviso aviso--aviso detalle__pendiente" role="status">
              <app-icono class="aviso__icono" nombre="reloj" />
              <div class="aviso__cuerpo"><p>{{ textoPendiente }}</p></div>
            </div>
          }
          <div class="accesos-accion">
            @if (pendiente()) {
              <button
                type="button"
                class="acceso-accion acceso-accion--principal"
                [disabled]="store.consultando()"
                (click)="store.consultarDeNuevo()"
              >
                <span class="acceso-accion__circulo"><app-icono nombre="reloj" /></span>Consultar de nuevo
              </button>
            }
            <a class="acceso-accion" [class.acceso-accion--principal]="!pendiente()" routerLink="/historial">
              <span class="acceso-accion__circulo"><app-icono nombre="pagos" /></span>Volver a mis pagos
            </a>
          </div>
        </app-comprobante-pago>
      } @else {
        <section class="franja oscuro" aria-labelledby="detalle-titulo">
          <div class="franja__contenido">
            <a class="volver" routerLink="/historial"><app-icono nombre="atras" /> Mis pagos</a>
            <h1 id="detalle-titulo">Detalle del pago</h1>
          </div>
        </section>
        <div class="hoja">
          <div class="hoja__contenido">
            @switch (store.estado()) {
              @case ('cargando') {
                <p class="cargando" role="status" aria-live="polite">
                  <span class="spinner" aria-hidden="true"></span> Cargando la operación…
                </p>
              }
              @case ('noEncontrado') {
                <section class="mensaje" role="status">
                  <span class="mensaje__icono"><app-icono nombre="buscar" /></span>
                  <p>{{ textoNoEncontrada }}</p>
                  <a class="btn" routerLink="/historial">Volver a mis pagos</a>
                </section>
              }
              @case ('error') {
                <section class="estado-error" role="alert">
                  <span class="estado-error__icono"><app-icono nombre="alerta" /></span>
                  <p class="estado-error__texto">{{ textoError }}</p>
                  <button type="button" class="btn" (click)="store.reintentar()">Reintentar</button>
                </section>
                <div class="acciones">
                  <a class="btn btn--sec" routerLink="/historial">Volver a mis pagos</a>
                </div>
              }
            }
          </div>
        </div>
      }
    </main>
  `,
})
export class DetallePagoPage {
  protected readonly store = inject(DetalleHistorialStore);
  protected readonly textoNoEncontrada = TEXTO_NO_ENCONTRADA;
  protected readonly textoError = TEXTO_ERROR_DETALLE;
  protected readonly textoPendiente = TEXTO_PENDIENTE_DETALLE;

  private readonly upc = toSignal(inject(ActivatedRoute).paramMap.pipe(map((p) => p.get('upc') ?? '')), {
    initialValue: '',
  });

  protected readonly pendiente = () => {
    const pago = this.store.pago();
    return pago !== null && estadoVisualDe(pago.estatus) === 'pendiente';
  };

  constructor() {
    void this.store.cargar(this.upc());
  }
}
