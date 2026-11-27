import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type NombreIcono =
  | 'check'
  | 'alerta'
  | 'reloj'
  | 'info'
  | 'atras'
  | 'derecha'
  | 'cerrar'
  | 'buscar'
  | 'copiar'
  | 'salir'
  | 'servicios'
  | 'pagos'
  | 'rayo'
  | 'gota'
  | 'telefono'
  | 'movil'
  | 'flama'
  | 'senal'
  | 'recibo'
  | 'enviar'
  | 'mas'
  | 'usuario'
  | 'casa'
  | 'ojo'
  | 'ojo-tachado';

/** Icono SVG simple de trazo; hereda el color del texto y es decorativo. */
@Component({
  selector: 'app-icono',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true' },
  styles: `
    :host {
      display: inline-flex;
      flex: none;
      width: 20px;
      height: 20px;
    }
    svg {
      width: 100%;
      height: 100%;
      fill: none;
      stroke: currentColor;
      stroke-width: 1.8;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
  `,
  template: `
    <svg viewBox="0 0 20 20" focusable="false">
      @switch (nombre()) {
        @case ('check') {
          <path d="M4 10.5l4 4 8-9" />
        }
        @case ('alerta') {
          <circle cx="10" cy="10" r="8" />
          <path d="M10 5.5v5.5M10 14.2v.1" />
        }
        @case ('info') {
          <circle cx="10" cy="10" r="8" />
          <path d="M10 9v5.5M10 6v.1" />
        }
        @case ('reloj') {
          <circle cx="10" cy="10" r="8" />
          <path d="M10 5.5V10l3 2" />
        }
        @case ('atras') {
          <path d="M12.5 4l-6 6 6 6" />
        }
        @case ('derecha') {
          <path d="M7.5 4l6 6-6 6" />
        }
        @case ('cerrar') {
          <path d="M5 5l10 10M15 5L5 15" />
        }
        @case ('buscar') {
          <circle cx="8.5" cy="8.5" r="5.5" />
          <path d="M12.6 12.6L17 17" />
        }
        @case ('copiar') {
          <rect x="7" y="7" width="10" height="10" rx="2" />
          <path d="M4 13V5a1 1 0 011-1h8" />
        }
        @case ('salir') {
          <path d="M8 3H4.5a1 1 0 00-1 1v12a1 1 0 001 1H8M12.5 6.5L16 10l-3.5 3.5M16 10H8" />
        }
        @case ('servicios') {
          <rect x="3" y="3" width="5.5" height="5.5" rx="1.5" />
          <rect x="11.5" y="3" width="5.5" height="5.5" rx="1.5" />
          <rect x="3" y="11.5" width="5.5" height="5.5" rx="1.5" />
          <rect x="11.5" y="11.5" width="5.5" height="5.5" rx="1.5" />
        }
        @case ('pagos') {
          <path d="M5 3h10v14l-2.5-1.5L10 17l-2.5-1.5L5 17z" />
          <path d="M8 7.5h4M8 10.5h4" />
        }
        @case ('rayo') {
          <path d="M11 2.5L4.5 11H10l-1 6.5L15.5 9H10z" />
        }
        @case ('gota') {
          <path d="M10 2.5S4.5 8.6 4.5 12a5.5 5.5 0 0011 0C15.5 8.6 10 2.5 10 2.5z" />
        }
        @case ('telefono') {
          <path d="M5 3h3l1.5 4-2 1.2a9 9 0 004.3 4.3l1.2-2 4 1.5v3a1.5 1.5 0 01-1.5 1.5A13.5 13.5 0 013.5 4.5 1.5 1.5 0 015 3z" />
        }
        @case ('movil') {
          <rect x="5.5" y="2.5" width="9" height="15" rx="2" />
          <path d="M9 14.5h2" />
        }
        @case ('flama') {
          <path d="M10 2.5c3 3 5 5.5 5 9a5 5 0 01-10 0c0-2 1-3.5 2-4.5 0 1.5 1 2.5 2 2.5 0-3 0-5 1-7z" />
        }
        @case ('senal') {
          <path d="M3 8a10 10 0 0114 0M5.5 10.8a6.5 6.5 0 019 0M8 13.5a3 3 0 014 0" />
          <path d="M10 16.2v.1" />
        }
        @case ('enviar') {
          <path d="M3 10h11M10 5.5l4.5 4.5-4.5 4.5" />
          <path d="M17 4v12" />
        }
        @case ('mas') {
          <circle cx="5" cy="10" r="1.2" />
          <circle cx="10" cy="10" r="1.2" />
          <circle cx="15" cy="10" r="1.2" />
        }
        @case ('casa') {
          <path d="M3 9.5L10 3.5l7 6V16a1 1 0 01-1 1h-3.5v-5h-5v5H4a1 1 0 01-1-1z" />
        }
        @case ('ojo') {
          <path d="M1.8 10S4.8 4.5 10 4.5 18.2 10 18.2 10 15.2 15.5 10 15.5 1.8 10 1.8 10z" />
          <circle cx="10" cy="10" r="2.5" />
        }
        @case ('ojo-tachado') {
          <path d="M1.8 10S4.8 4.5 10 4.5 18.2 10 18.2 10 15.2 15.5 10 15.5 1.8 10 1.8 10z" />
          <circle cx="10" cy="10" r="2.5" />
          <path d="M3.5 3.5l13 13" />
        }
        @case ('usuario') {
          <circle cx="10" cy="7" r="3.5" />
          <path d="M3.5 17a6.5 6.5 0 0113 0" />
        }
        @case ('recibo') {
          <path d="M5 2.5h10v15l-2-1.2-1.5 1.2-1.5-1.2-1.5 1.2L7 16.3l-2 1.2z" />
          <path d="M8 6.5h4M8 9.5h4M8 12.5h2" />
        }
      }
    </svg>
  `,
})
export class Icono {
  readonly nombre = input.required<NombreIcono>();
}
