import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FiltroEstado } from '../../domain/models/historial';
import { FILTROS_ESTADO } from '../../domain/models/mensajes-historial';

/** Orden de presentación de los filtros (igual en web y móvil); las etiquetas vienen del dominio. */
const ORDEN: FiltroEstado[] = ['todos', 'exitosos', 'pendientes', 'rechazados'];

/** Chips rectangulares para filtrar por estado; el seleccionado va relleno (invertido sobre la franja negra). */
@Component({
  selector: 'app-historial-filtros',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: block;
    }
    .chips {
      display: flex;
      gap: var(--s-2);
      margin: var(--s-5) calc(-1 * var(--margen)) 0;
      padding: var(--s-1) var(--margen);
      overflow-x: auto;
      scrollbar-width: none;
    }
    .chip {
      position: relative;
      flex: none;
      min-height: 40px;
      padding: 0 var(--s-4);
      color: var(--texto);
      font-size: var(--t-14);
      font-weight: 500;
      white-space: nowrap;
      background: var(--circulo);
      border: 1px solid var(--borde-fuerte);
      border-radius: var(--r-control);
      cursor: pointer;
      transition:
        background-color 0.12s,
        border-color 0.12s;
    }
    /* Área táctil de 48 px sin agrandar el chip */
    .chip::before {
      position: absolute;
      inset: -4px 0;
      content: '';
    }
    .chip:hover {
      background: var(--hover);
    }
    .chip--activo,
    .chip--activo:hover {
      color: var(--primario-texto);
      font-weight: 600;
      background: var(--primario);
      border-color: var(--primario);
      box-shadow: var(--boton-filo);
    }
  `,
  template: `
    <div class="chips" role="group" aria-label="Filtrar por estado">
      @for (f of filtros; track f.valor) {
        <button
          type="button"
          class="chip"
          [class.chip--activo]="seleccionado() === f.valor"
          [attr.aria-pressed]="seleccionado() === f.valor"
          (click)="cambia.emit(f.valor)"
        >
          {{ f.etiqueta }}
        </button>
      }
    </div>
  `,
})
export class HistorialFiltros {
  readonly seleccionado = input.required<FiltroEstado>();
  readonly cambia = output<FiltroEstado>();
  protected readonly filtros = ORDEN.map((valor) => FILTROS_ESTADO.find((f) => f.valor === valor)!);
}
