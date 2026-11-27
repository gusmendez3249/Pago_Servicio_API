import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';

/**
 * Banner con foto: la foto arriba con proporción fija (sin saltos de layout), carga diferida y `object-fit: cover`;
 * el texto va debajo, sobre la tarjeta blanca (nunca encima de la foto). Mientras la foto carga, o si no existe,
 * se ve un respaldo plano con la marca de plantilla a gran tamaño y recortada.
 */
@Component({
  selector: 'app-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.banner--sangre]': 'sangre()' },
  styles: `
    :host {
      display: block;
      overflow: hidden;
      background: var(--superficie);
      border: 1px solid var(--borde);
      border-radius: var(--r-tarjeta);
    }
    :host(.banner--sangre) {
      border: 0;
      border-radius: 0;
    }
    .banner__media {
      position: relative;
      overflow: hidden;
      background: var(--fondo);
    }
    /* Contenido secundario: alto máximo 200 px (móvil) y 240 px (escritorio) */
    :host(:not(.banner--sangre)) .banner__media {
      max-height: 200px;
    }
    @media (min-width: 64rem) {
      :host(:not(.banner--sangre)) .banner__media {
        max-height: 240px;
      }
    }
    :host(.banner--sangre) .banner__media {
      height: 100%;
    }
    .banner__respaldo,
    .banner__img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
    }
    .banner__img {
      object-fit: cover;
      opacity: 0;
    }
    .banner__img--lista {
      opacity: 1;
    }
    .banner__pie {
      margin: 0;
      padding: var(--s-3) var(--s-4) var(--s-4);
    }
    .banner__pie strong {
      display: block;
      font-size: var(--t-16);
      font-weight: 600;
    }
    .banner__pie span {
      display: block;
      color: var(--texto-2);
      font-size: var(--t-14);
    }
  `,
  template: `
    <div class="banner__media" [style.aspect-ratio]="proporcion()">
      <svg class="banner__respaldo" viewBox="0 0 160 100" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
        <path
          transform="translate(70 -30) scale(2.6)"
          fill="#e3e5e8"
          d="M16 12h9v40h-9ZM28 12h5a9.5 9.5 0 0 1 0 19h-5ZM28 33h10a9.5 9.5 0 0 1 0 19H28Z"
        />
      </svg>
      @if (!fallo()) {
        <img
          class="banner__img"
          [class.banner__img--lista]="lista()"
          [src]="imagen()"
          [alt]="alt()"
          [style.object-position]="enfoque()"
          [attr.loading]="prioridad() ? 'eager' : 'lazy'"
          [attr.fetchpriority]="prioridad() ? 'high' : null"
          decoding="async"
          (load)="lista.set(true)"
          (error)="fallo.set(true)"
        />
      }
    </div>
    @if (pieTitulo()) {
      <p class="banner__pie">
        <strong>{{ pieTitulo() }}</strong>
        @if (pieTexto()) {
          <span>{{ pieTexto() }}</span>
        }
      </p>
    }
  `,
})
export class Banner {
  /** Ruta de la foto dentro de `public/` (p. ej. `banners/inicio.jpg`). */
  readonly imagen = input.required<string>();
  /** Descripción de la foto. */
  readonly alt = input('');
  /** Proporción CSS fija de la foto (16:7 por defecto); `auto` cuando el contenedor fija el alto. */
  readonly proporcion = input('16 / 7');
  /** Punto de la foto que se conserva al recortar (`object-position`). */
  readonly enfoque = input('50% 50%');
  /** Foto visible al abrir la pantalla (login): carga inmediata en vez de diferida. */
  readonly prioridad = input(false);
  /** A sangre: sin borde ni esquinas (login). */
  readonly sangre = input(false);
  readonly pieTitulo = input<string | null>(null);
  readonly pieTexto = input<string | null>(null);

  protected readonly lista = signal(false);
  protected readonly fallo = signal(false);
}
