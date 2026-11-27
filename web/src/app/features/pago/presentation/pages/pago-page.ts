import { ChangeDetectionStrategy, Component, OnDestroy, computed, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';
import { CabeceraFlujo } from '../../../../shared/ui/cabecera-flujo/cabecera-flujo';
import { Pasos } from '../../../../shared/ui/pasos/pasos';
import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { LogoServicio } from '../../../catalogo/presentation/components/logo-servicio';
import { CatalogoStore } from '../../../catalogo/presentation/store/catalogo.store';
import { ValidacionReferenciaStore } from '../../../referencia/presentation/store/validacion-referencia.store';
import { ProductoPago } from '../../domain/models/pago';
import { CapturaDatos } from '../components/captura-datos';
import { ModalConfirmacion } from '../components/modal-confirmacion';
import { ResultadoPagoVista } from '../components/resultado-pago';
import { ResumenPago } from '../components/resumen-pago';
import { PagoStore } from '../store/pago.store';

/** Pantalla de pago: orquesta captura, resumen, confirmación con PIN y resultado según el estado del store. */
@Component({
  selector: 'app-pago-page',
  imports: [CabeceraFlujo, CapturaDatos, LogoServicio, ModalConfirmacion, Pasos, ResultadoPagoVista, ResumenPago],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .pago__lado-titulo {
      margin-bottom: var(--s-3);
      font-size: var(--t-16);
    }
  `,
  host: { '(window:beforeunload)': 'alCerrarVentana($event)' },
  template: `
    <div [attr.inert]="store.modalAbierto() ? '' : null">
      <app-cabecera-flujo
        [titulo]="enResultado() ? null : titulo()"
        [volver]="['/catalogo', ids().idServicio, ids().idProducto]"
        textoVolver="Volver al servicio"
      />
      <main>
        @if (enResultado()) {
          <app-resultado-pago />
        } @else {
          <section class="franja oscuro" aria-labelledby="flujo-titulo">
            <div class="franja__contenido">
              @if (pasoActual(); as paso) {
                <app-pasos [actual]="paso" [directo]="store.modo() === 'telefono'" />
              }
              <div class="franja__servicio">
                @if (store.producto(); as p) {
                  <app-logo-servicio [url]="p.logoUrl" [nombre]="p.servicio" tamano="grande" />
                }
                @if (store.producto(); as p) {
                  <div>
                    <p class="franja__nombre">{{ p.servicio }}</p>
                    <p class="franja__sub">{{ p.producto }}</p>
                  </div>
                }
              </div>
              @if (store.total(); as total) {
                <p class="franja__dato">
                  <span class="franja__etiqueta">Importe a pagar</span>
                  <span class="cifra cifra--40">{{ moneda(total) }}</span>
                </p>
              }
            </div>
          </section>

          <div class="hoja">
            <div class="hoja__contenido">
              @switch (store.estado()) {
                @case ('preparando') {
                  <p class="cargando" role="status"><span class="spinner" aria-hidden="true"></span> Preparando tu pago…</p>
                }
                @case ('capturandoDatos') {
                  <div class="flujo">
                    <section class="tarjeta" aria-label="Datos del pago">
                      <app-captura-datos />
                    </section>
                    @if (store.producto(); as p) {
                      <aside class="flujo__lado tarjeta" aria-labelledby="pago-lado">
                        <h2 id="pago-lado" class="pago__lado-titulo">Tu pago</h2>
                        <dl class="ficha">
                          <dt>Servicio</dt>
                          <dd>{{ p.servicio }}</dd>
                          <dt>Producto</dt>
                          <dd>{{ p.producto }}</dd>
                          @if (store.modo() === 'referencia') {
                            <dt>Referencia</dt>
                            <dd>{{ store.destinoPago() }}</dd>
                          }
                        </dl>
                      </aside>
                    }
                  </div>
                }
                @default {
                  <app-resumen-pago />
                }
              }
            </div>
          </div>
        }
        <p class="solo-lectores" role="status" aria-live="polite">{{ anuncio() }}</p>
      </main>
    </div>
    @if (store.modalAbierto()) {
      <app-modal-confirmacion
        [total]="store.total() ?? 0"
        [procesando]="store.procesando()"
        [aviso]="store.avisoPin()"
        (confirmar)="confirmar($event)"
        (cancelar)="store.cancelarConfirmacion()"
      />
    }
  `,
})
export class PagoPage implements OnDestroy {
  protected readonly store = inject(PagoStore);
  private readonly catalogo = inject(CatalogoStore);
  private readonly validacion = inject(ValidacionReferenciaStore);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly ids = toSignal(
    this.ruta.paramMap.pipe(
      map((p) => ({ idServicio: Number(p.get('idServicio')), idProducto: Number(p.get('idProducto')) })),
    ),
    { requireSync: true },
  );

  protected readonly enResultado = computed(() => ['exitoso', 'rechazado', 'pendiente', 'error'].includes(this.store.estado()));

  /** Paso del indicador; sin paso en el resultado (la cabecera muestra solo el logotipo). */
  protected readonly pasoActual = computed<1 | 2 | 3 | null>(() => {
    if (this.enResultado()) return null;
    return this.store.estado() === 'capturandoDatos' && this.store.modo() === 'telefono' ? 2 : 3;
  });

  protected readonly titulo = computed(() => {
    switch (this.store.estado()) {
      case 'preparando':
        return 'Preparando tu pago';
      case 'capturandoDatos':
        return this.store.modo() === 'telefono' ? 'Captura el teléfono' : 'Datos del pago';
      default:
        return 'Revisa y confirma';
    }
  });

  /** Anuncio breve para lectores de pantalla en cada cambio de estado. */
  protected readonly anuncio = computed(() => {
    switch (this.store.estado()) {
      case 'procesando':
        return 'Procesando tu pago';
      case 'exitoso':
        return 'Pago realizado';
      case 'rechazado':
        return 'No se realizó el pago';
      case 'pendiente':
        return 'Estamos confirmando tu pago';
      default:
        return '';
    }
  });

  private claveIniciada: string | null = null;

  constructor() {
    void this.catalogo.cargar();
    effect(() => {
      const { idServicio, idProducto } = this.ids();
      const p = this.catalogo.buscarProducto(idServicio, idProducto);
      if (p === undefined || p.idServicio === null || p.idProducto === null || p.tipoFront === null) return;
      const clave = `${idServicio}-${idProducto}`;
      if (clave === this.claveIniciada) return;
      untracked(() => {
        const producto: ProductoPago = {
          idSer: p.idServicio as number,
          idProd: p.idProducto as number,
          servicio: p.servicio,
          producto: p.producto,
          logoUrl: p.logoUrl,
          tipoFront: p.tipoFront as number,
          precio: p.precio,
        };
        if (p.tipoFront === 1) {
          this.claveIniciada = clave;
          void this.store.iniciar({ producto });
          return;
        }
        const d = this.validacion.datosPago();
        if (d === null) return;
        this.claveIniciada = clave;
        void this.store.iniciar({
          producto,
          referencia: { referencia: d.referencia, idValidacion: d.idValidacion, verificacion: d.verificacion, montoAPagar: d.montoAPagar },
        });
      });
    });
  }

  protected moneda(valor: number): string {
    return formatoMoneda(valor);
  }

  ngOnDestroy(): void {
    this.store.salir();
  }

  protected confirmar(pin: string): void {
    void this.store.confirmar(pin);
  }

  /** Aviso del navegador al cerrar o recargar mientras el pago está en proceso. */
  protected alCerrarVentana(evento: BeforeUnloadEvent): void {
    if (this.store.procesando()) evento.preventDefault();
  }
}
