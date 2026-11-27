import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  TEXTO_GENERICO,
  TEXTO_NO_CARGO,
  TEXTO_PIN_BLOQUEADO,
  TEXTO_NO_LLEGO,
  TEXTO_SIN_CONEXION,
  TEXTO_SIN_CUENTA,
  TEXTO_VALIDACION_EXPIRADA,
  tituloDeRechazo,
  TextoResultado,
  textoExitoso,
  textoPendiente,
  textoRechazo,
} from '../../domain/models/mensajes-pago';
import { PagoStore } from '../store/pago.store';
import { ComprobantePago } from './comprobante-pago';
import { Icono, NombreIcono } from '../../../../shared/ui/icono/icono';
import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';

/** Resultado del pago: franja con icono de estado (éxito, pendiente, no aplicado), título e importe. */
@Component({
  selector: 'app-resultado-pago',
  imports: [ComprobantePago, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .resultado__estado {
      display: flex;
      gap: var(--s-4);
      align-items: center;
    }
    .resultado__importe {
      margin: var(--s-5) 0 0;
    }
    .resultado__cuerpo .tarjeta > p {
      margin: 0;
      overflow-wrap: anywhere;
    }
    .resultado__cuerpo .tarjeta > p + p {
      margin-top: var(--s-3);
    }
    .resultado__sondeo {
      display: flex;
      gap: var(--s-2);
      align-items: center;
      color: var(--texto-2);
    }
  `,
  template: `
    <div aria-live="polite" aria-atomic="true">
      @if (store.comprobanteVisible() && store.resultado(); as pago) {
        <app-comprobante-pago
          [pago]="pago"
          [servicioNombre]="store.producto()?.servicio ?? ''"
          [productoNombre]="store.producto()?.producto ?? ''"
          [totalPagado]="store.totalFinal()"
        >
          <div class="accesos-accion">
            <button type="button" class="acceso-accion acceso-accion--principal" (click)="irAlInicio()">
              <span class="acceso-accion__circulo"><app-icono nombre="servicios" /></span>Ir al inicio
            </button>
            <button type="button" class="acceso-accion" (click)="verMisPagos()">
              <span class="acceso-accion__circulo"><app-icono nombre="pagos" /></span>Ver mis pagos
            </button>
            <button type="button" class="acceso-accion" (click)="store.ocultarComprobante()">
              <span class="acceso-accion__circulo"><app-icono nombre="atras" /></span>Volver al resultado
            </button>
          </div>
        </app-comprobante-pago>
      } @else {
        <section class="franja oscuro" aria-labelledby="res-titulo" [attr.aria-busy]="store.estado() === 'pendiente' ? store.sondeando() : null">
          <div class="franja__contenido">
            <div class="resultado__estado">
              <span [class]="'estado-icono estado-icono--' + vista().clase"><app-icono [nombre]="vista().icono" /></span>
              <h1 id="res-titulo">{{ vista().titulo }}</h1>
            </div>
            @if (vista().importe; as importe) {
              <p class="resultado__importe">
                <span class="franja__etiqueta">{{ store.estado() === 'exitoso' ? 'Pagaste' : 'Importe' }}</span>
                <span class="cifra cifra--40">{{ importe }}</span>
              </p>
            }
          </div>
        </section>

        <div class="hoja">
          <div class="hoja__contenido">
            <div class="columna-8 resultado__cuerpo">
              <div class="tarjeta">
                @for (texto of vista().cuerpo; track $index) {
                  <p>{{ texto }}</p>
                }
                @if (store.estado() === 'pendiente' && store.sondeando()) {
                  <p class="resultado__sondeo"><span class="spinner" aria-hidden="true"></span> Consultando el estado…</p>
                }
              </div>
              @switch (store.estado()) {
                @case ('exitoso') {
                  <div class="acciones">
                    <button type="button" class="btn" (click)="store.verComprobante()">Ver comprobante</button>
                    <button type="button" class="btn btn--sec" (click)="verMisPagos()">Ver mis pagos</button>
                    <button type="button" class="btn btn--sec" (click)="irAlInicio()">Ir al inicio</button>
                  </div>
                }
                @case ('rechazado') {
                  <div class="acciones">
                    <button type="button" class="btn" (click)="accionRechazo()">{{ rechazo().accion }}</button>
                    <button type="button" class="btn btn--sec" (click)="verMisPagos()">Ver mis pagos</button>
                  </div>
                }
                @case ('pendiente') {
                  <div class="acciones">
                    @if (store.noLlego()) {
                      <button type="button" class="btn" (click)="store.reintentarPago()">Reintentar pago</button>
                    }
                    <button
                      type="button"
                      class="btn"
                      [class.btn--sec]="store.noLlego()"
                      [disabled]="store.consultando()"
                      (click)="store.consultarAhora()"
                    >
                      Consultar de nuevo
                    </button>
                    <button type="button" class="btn btn--sec" (click)="verMisPagos()">Ver mis pagos</button>
                    <button type="button" class="btn btn--sec" (click)="irAlInicio()">Ir al inicio</button>
                  </div>
                }
                @case ('error') {
                  <div class="acciones">
                    <button type="button" class="btn" (click)="accionError()">{{ etiquetaError() }}</button>
                  </div>
                }
              }
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class ResultadoPagoVista {
  protected readonly store = inject(PagoStore);
  private readonly router = inject(Router);

  protected readonly tituloRechazo = computed(() => tituloDeRechazo(this.store.motivoRechazo()));
  protected readonly noCargo = TEXTO_NO_CARGO;

  protected readonly exitoso = computed(() => {
    const pago = this.store.resultado();
    const servicio = pago?.servicio ?? this.store.producto()?.servicio ?? '';
    const total = pago?.monto ?? this.store.totalFinal() ?? 0;
    return textoExitoso(servicio, total, pago?.folio ?? null);
  });
  protected readonly rechazo = computed(() => textoRechazo(this.store.motivoRechazo()));
  protected readonly pendiente = computed<TextoResultado>(() =>
    this.store.noLlego()
      ? TEXTO_NO_LLEGO
      : this.store.sinConexion()
        ? TEXTO_SIN_CONEXION
        : textoPendiente(this.store.upc()),
  );
  protected readonly error = computed<TextoResultado>(() => {
    switch (this.store.motivoError()) {
      case 'SIN_CUENTA':
        return TEXTO_SIN_CUENTA;
      case 'PIN_BLOQUEADO':
        return TEXTO_PIN_BLOQUEADO;
      case 'VALIDACION_EXPIRADA':
        return TEXTO_VALIDACION_EXPIRADA;
      default:
        return TEXTO_GENERICO;
    }
  });
  protected readonly etiquetaError = computed(() => {
    switch (this.store.motivoError()) {
      case 'SIN_CUENTA':
      case 'PIN_BLOQUEADO':
        return 'Ir al inicio';
      case 'VALIDACION_EXPIRADA':
        return 'Validar de nuevo';
      default:
        return 'Volver';
    }
  });

  /** Lo que pinta la franja y la tarjeta según el estado; los textos vienen de mensajes-pago. */
  protected readonly vista = computed<{ clase: string; icono: NombreIcono; titulo: string; cuerpo: string[]; importe: string | null }>(() => {
    const total = this.store.resultado()?.monto ?? this.store.totalFinal();
    const importe = total === null || total === undefined ? null : formatoMoneda(total);
    switch (this.store.estado()) {
      case 'exitoso':
        return { clase: 'exitoso', icono: 'check', titulo: this.exitoso().titulo, cuerpo: [this.exitoso().cuerpo], importe };
      case 'rechazado':
        return { clase: 'rechazado', icono: 'alerta', titulo: this.tituloRechazo(), cuerpo: [this.rechazo().cuerpo, this.noCargo], importe: null };
      case 'pendiente':
        return { clase: 'pendiente', icono: 'reloj', titulo: this.pendiente().titulo, cuerpo: [this.pendiente().cuerpo], importe };
      default:
        return { clase: 'rechazado', icono: 'alerta', titulo: this.error().titulo, cuerpo: [this.error().cuerpo], importe: null };
    }
  });

  protected verMisPagos(): void {
    void this.router.navigate(['/historial']);
  }

  protected irAlInicio(): void {
    void this.router.navigate(['/catalogo']);
  }

  protected accionRechazo(): void {
    const producto = this.store.producto();
    switch (this.store.motivoRechazo()) {
      case 'REFERENCIA_INVALIDA':
      case 'RECHAZADO_PROVEEDOR':
        // Pago con referencia: se vuelve a validar (el comprobante ya se consumió). Con teléfono: se reinicia la captura.
        if (this.store.intentarDeNuevo() === 'referencia') this.aReferencia();
        return;
      default:
        if (producto !== null) void this.router.navigate(['/catalogo']);
    }
  }

  protected accionError(): void {
    const producto = this.store.producto();
    switch (this.store.motivoError()) {
      case 'SIN_CUENTA':
      case 'PIN_BLOQUEADO':
        this.irAlInicio();
        return;
      case 'VALIDACION_EXPIRADA':
        this.aReferencia();
        return;
      default:
        if (producto !== null) void this.router.navigate(['/catalogo', producto.idSer, producto.idProd]);
        else this.irAlInicio();
    }
  }

  private aReferencia(): void {
    const p = this.store.producto();
    if (p === null) {
      this.irAlInicio();
      return;
    }
    void this.router.navigate(['/catalogo', p.idSer, p.idProd, 'referencia']);
  }
}
