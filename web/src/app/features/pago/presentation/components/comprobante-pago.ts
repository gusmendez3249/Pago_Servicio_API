import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { Icono, NombreIcono } from '../../../../shared/ui/icono/icono';
import { RouterLink } from '@angular/router';
import { Logotipo } from '../../../../shared/ui/logotipo/logotipo';
import { LogoServicio } from '../../../catalogo/presentation/components/logo-servicio';
import { formatoFechaHora } from '../../../historial/domain/models/historial-reglas';
import { ETIQUETA_ESTATUS, textoRechazo } from '../../domain/models/mensajes-pago';
import { motivoRechazoDe } from '../../domain/models/pago-reglas';
import { ResultadoPago } from '../../domain/models/pago';

/** Comprobante: franja con estado e importe y ficha de datos con folio copiable. `pinServicio` y `leyenda` solo se muestran aquí y solo si el pago fue exitoso. */
@Component({
  selector: 'app-comprobante-pago',
  imports: [Icono, LogoServicio, Logotipo, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: block;
    }
    .comprobante__estado {
      display: flex;
      gap: var(--s-4);
      align-items: center;
    }
    .comprobante__cabecera {
      display: flex;
      flex-wrap: wrap;
      gap: var(--s-3);
      align-items: center;
      justify-content: space-between;
      margin-bottom: var(--s-4);
      padding-bottom: var(--s-4);
      border-bottom: 1px solid var(--borde);
    }
    .comprobante__importe {
      margin: var(--s-5) 0 0;
    }
    .comprobante__marca {
      display: flex;
      gap: var(--s-3);
      align-items: center;
      margin-bottom: var(--s-4);
    }
    .comprobante__marca p {
      margin: 0;
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .comprobante__copiable {
      display: flex;
      flex-wrap: wrap;
      gap: var(--s-2) var(--s-4);
      align-items: center;
      justify-content: space-between;
      margin-top: var(--s-5);
      padding: var(--s-4);
      background: var(--hover);
      border-radius: var(--r-control);
    }
    .comprobante__copiable-texto {
      min-width: 0;
    }
    .comprobante__copiable-etiqueta {
      display: block;
      color: var(--texto-2);
      font-size: var(--t-14);
    }
    .comprobante__copiable-valor {
      font-size: var(--t-20);
    }
    .comprobante__copiar {
      min-height: 44px;
      padding: 0 var(--s-4);
      font-size: var(--t-14);
    }
    .comprobante__nota {
      margin: var(--s-4) 0 0;
      white-space: pre-line;
      overflow-wrap: anywhere;
    }
  `,
  template: `
    <section class="franja oscuro" aria-labelledby="comprobante-titulo">
      <div class="franja__contenido">
        @if (volver(); as v) {
          <a class="volver" [routerLink]="v.ruta"><app-icono nombre="atras" /> {{ v.texto }}</a>
        }
        <div class="comprobante__estado">
          <span [class]="'estado-icono estado-icono--' + estado().clase"><app-icono [nombre]="estado().icono" /></span>
          <h1 id="comprobante-titulo">{{ estado().texto }}</h1>
        </div>
        <p class="comprobante__importe">
          <span class="franja__etiqueta">Total</span>
          <span class="cifra cifra--40">{{ total() }}</span>
        </p>
      </div>
    </section>

    <div class="hoja">
      <div class="hoja__contenido">
        <div class="columna-8">
          <article class="tarjeta" aria-labelledby="comprobante-ficha">
            <header class="comprobante__cabecera">
              <h2 id="comprobante-ficha">Comprobante</h2>
              <app-logotipo />
            </header>
            @if (conLogo()) {
              <div class="comprobante__marca">
                <app-logo-servicio [url]="pago().logoUrl ?? null" [nombre]="servicio()" />
                <p>{{ servicio() }}</p>
              </div>
            }
            <dl class="ficha">
              <dt>Servicio</dt>
              <dd>{{ servicio() }}</dd>
              <dt>Producto</dt>
              <dd>{{ producto() }}</dd>
              @if (pago().telefonoEnmascarado; as t) {
                <dt>Teléfono</dt>
                <dd>{{ t }}</dd>
              }
              @if (pago().referenciaEnmascarada; as r) {
                <dt>Referencia</dt>
                <dd>{{ r }}</dd>
              }
              @if (pago().numCuentaEnmascarada; as c) {
                <dt>Cuenta</dt>
                <dd>{{ c }}</dd>
              }
              @if (fecha(); as f) {
                <dt>Fecha y hora</dt>
                <dd>{{ f }}</dd>
              }
              <dt>Estatus</dt>
              <dd>{{ estatus() }}</dd>
            </dl>

            <div class="comprobante__copiable">
              <span class="comprobante__copiable-texto">
                <span class="comprobante__copiable-etiqueta">{{ pago().folio ? 'Folio' : 'Número de operación' }}</span>
                <span class="cifra comprobante__copiable-valor">{{ identificador() }}</span>
              </span>
              @if (identificador() !== '—') {
                <button type="button" class="btn btn--sec comprobante__copiar" (click)="copiar('folio', identificador())">
                  <app-icono nombre="copiar" /> {{ copiado() === 'folio' ? 'Copiado' : 'Copiar' }}
                </button>
              }
            </div>

            @if (exitoso() && pago().pinServicio; as codigo) {
              <div class="comprobante__copiable">
                <span class="comprobante__copiable-texto">
                  <span class="comprobante__copiable-etiqueta">Código de tu tarjeta</span>
                  <span class="cifra comprobante__copiable-valor">{{ codigo }}</span>
                </span>
                <button type="button" class="btn btn--sec comprobante__copiar" (click)="copiar('codigo', codigo)">
                  <app-icono nombre="copiar" /> {{ copiado() === 'codigo' ? 'Copiado' : 'Copiar' }}
                </button>
              </div>
            }
            @if (motivo(); as m) {
              <p class="comprobante__nota">{{ m }}</p>
            }
            @if (exitoso() && pago().leyenda; as l) {
              <p class="comprobante__nota">{{ l }}</p>
            }
          </article>
          <ng-content />
        </div>
      </div>
    </div>
  `,
})
export class ComprobantePago {
  readonly pago = input.required<ResultadoPago>();
  readonly servicioNombre = input('');
  readonly productoNombre = input('');
  readonly totalPagado = input<number | null>(null);
  /** Muestra el logotipo del servicio (el historial lo pide; el flujo de pago no). */
  readonly conLogo = input(false);
  /** Enlace de regreso arriba a la izquierda de la franja (el historial lo usa; el flujo de pago no). */
  readonly volver = input<{ ruta: string; texto: string } | null>(null);

  /** Qué dato se copió al último: el folio o el código de la tarjeta. */
  protected readonly copiado = signal<'folio' | 'codigo' | null>(null);

  protected readonly exitoso = computed(() => this.pago().estatus === 'CONFIRMADA');
  protected readonly servicio = computed(() => this.pago().servicio ?? this.servicioNombre());
  protected readonly producto = computed(() => this.pago().producto ?? this.productoNombre());
  protected readonly total = computed(() => {
    const m = this.pago().monto ?? this.totalPagado();
    return m === null ? '—' : formatoMoneda(m);
  });
  protected readonly identificador = computed(() => {
    const p = this.pago();
    return p.folio ?? p.identificadorOperacion ?? p.idTx ?? p.upc ?? '—';
  });
  protected readonly estado = computed<{ texto: string; clase: string; icono: NombreIcono }>(() => {
    switch (this.pago().estatus) {
      case 'CONFIRMADA':
        return { texto: 'Pago realizado', clase: 'exitoso', icono: 'check' };
      case 'RECHAZADA':
        return { texto: 'No aplicado', clase: 'rechazado', icono: 'alerta' };
      default:
        return { texto: 'Pendiente de confirmar', clase: 'pendiente', icono: 'reloj' };
    }
  });
  protected readonly estatus = computed(() => ETIQUETA_ESTATUS[this.pago().estatus]);
  protected readonly fecha = computed(() => formatoFechaHora(this.pago().fechaTransaccion));
  /** Motivo amigable (mismos textos del flujo de pago) solo si la operación fue rechazada. */
  protected readonly motivo = computed(() =>
    this.pago().estatus === 'RECHAZADA' ? textoRechazo(motivoRechazoDe(this.pago().motivo)).cuerpo : null,
  );

  /** Copia al portapapeles; si no hay permiso o API, falla en silencio. */
  protected async copiar(cual: 'folio' | 'codigo', valor: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(valor);
      this.copiado.set(cual);
    } catch {
      this.copiado.set(null);
    }
  }
}
