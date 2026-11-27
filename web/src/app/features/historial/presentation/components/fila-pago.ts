import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icono, NombreIcono } from '../../../../shared/ui/icono/icono';
import { LogoServicio } from '../../../catalogo/presentation/components/logo-servicio';
import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { ElementoHistorial } from '../../domain/models/historial';
import {
  destinoDe,
  estadoVisualDe,
  etiquetaEstado,
  etiquetaIdentificador,
  formatoFechaHora,
  identificadorDe,
} from '../../domain/models/historial-reglas';

/** Renglón de un movimiento: logotipo, qué se pagó, cuándo, a qué referencia, cuánto y cómo quedó. */
@Component({
  selector: 'app-fila-pago',
  imports: [Icono, LogoServicio, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: block;
    }
  `,
  template: `
    @let p = pago();
    <a class="renglon" [routerLink]="p.upc ? ['/historial', p.upc] : null">
      <app-logo-servicio [url]="p.logoUrl ?? null" [nombre]="p.servicio ?? ''" />
      <span class="renglon__texto">
        <strong class="renglon__titulo">{{ p.servicio || 'Servicio' }}</strong>
        @if (p.producto) {
          <span class="renglon__sub">{{ p.producto }}</span>
        }
        <span class="renglon__meta">
          @if (hora(); as h) {
            <span>{{ h }}</span>
          }
          @if (destino(); as d) {
            <span>{{ d }}</span>
          }
          @if (identificador(); as i) {
            <span>{{ etiquetaId() }} {{ i }}</span>
          }
        </span>
      </span>
      <span class="renglon__lado">
        @if (monto(); as m) {
          <span class="renglon__monto">{{ m }}</span>
        }
        <span [class]="'chip-estado chip-estado--' + estado()">
          <app-icono [nombre]="icono()" />{{ etiqueta() }}
        </span>
      </span>
    </a>
  `,
})
export class FilaPago {
  readonly pago = input.required<ElementoHistorial>();

  protected readonly estado = computed(() => estadoVisualDe(this.pago().estatus));
  protected readonly etiqueta = computed(() => etiquetaEstado(this.estado()));
  /** Solo la hora: la fecha va en el encabezado del grupo. Si no se puede interpretar, el texto completo. */
  protected readonly hora = computed(() => {
    const texto = formatoFechaHora(this.pago().fechaTransaccion);
    return texto?.includes(', ') ? texto.slice(texto.lastIndexOf(', ') + 2) : texto;
  });
  protected readonly icono = computed<NombreIcono>(() => {
    switch (this.estado()) {
      case 'exitoso':
        return 'check';
      case 'rechazado':
        return 'alerta';
      default:
        return 'reloj';
    }
  });
  protected readonly destino = computed(() => destinoDe(this.pago()));
  protected readonly identificador = computed(() => identificadorDe(this.pago()));
  protected readonly etiquetaId = computed(() => etiquetaIdentificador(this.pago()));
  protected readonly monto = computed(() => {
    const m = this.pago().monto;
    return m === null ? null : formatoMoneda(m);
  });
}
