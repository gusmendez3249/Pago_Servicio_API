import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icono } from '../icono/icono';
import { Logotipo } from '../logotipo/logotipo';

/**
 * Barra superior del flujo de pago, sin navegación principal: flecha de regreso, título serif del paso
 * (el único título de la pantalla) y "Cancelar" como salida. Sin título (resultado del pago) muestra solo
 * el logotipo. El indicador "Paso n de 3" va debajo, en la franja de cada pantalla.
 */
@Component({
  selector: 'app-cabecera-flujo',
  imports: [Icono, Logotipo, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      position: absolute;
      inset: 0 0 auto;
      z-index: 10;
      display: block;
      color: #fff;
      background: var(--marca);
      border-bottom: 1px solid var(--marca-divisor);
      /* Pieza con la franja: filo de luz arriba; el de sombra lo pone la franja abajo */
      box-shadow: inset 0 1px 0 var(--filo-luz);
    }
    .flujo-cab {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      gap: var(--s-2);
      align-items: center;
      max-width: calc(var(--ancho-pagina) + 2 * var(--margen));
      height: var(--alto-cabecera);
      margin: 0 auto;
      padding: 0 var(--margen);
    }
    .flujo-cab__volver,
    .flujo-cab__cancelar {
      display: inline-flex;
      align-items: center;
      min-width: 48px;
      min-height: 48px;
      color: var(--texto);
      font-weight: 500;
      text-decoration: none;
      border-radius: var(--r-control);
    }
    .flujo-cab__volver {
      justify-content: flex-start;
      margin-left: calc(-1 * var(--s-1));
    }
    .flujo-cab__volver app-icono {
      width: 24px;
      height: 24px;
    }
    .flujo-cab__titulo {
      overflow: hidden;
      font-size: var(--t-20);
      line-height: 1.2;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .flujo-cab__cancelar {
      justify-content: flex-end;
      color: var(--texto);
      text-decoration: underline;
      text-underline-offset: 3px;
    }
    .flujo-cab__cancelar:hover {
      text-decoration-thickness: 2px;
    }
    .flujo-cab__marca {
      grid-column: 1 / -1;
      justify-self: start;
    }
  `,
  template: `
    <header class="flujo-cab oscuro">
      @if (titulo(); as t) {
        @if (volver(); as ruta) {
          <a class="flujo-cab__volver" [routerLink]="ruta" [attr.aria-label]="textoVolver()"><app-icono nombre="atras" /></a>
        } @else {
          <span></span>
        }
        <h1 class="flujo-cab__titulo" id="flujo-titulo">{{ t }}</h1>
        <a class="flujo-cab__cancelar" routerLink="/catalogo">Cancelar</a>
      } @else {
        <app-logotipo class="flujo-cab__marca" fondo="oscuro" />
      }
    </header>
  `,
})
export class CabeceraFlujo {
  /** Título serif del paso; null en el resultado del pago (solo logotipo). */
  readonly titulo = input<string | null>(null);
  readonly volver = input<string | (string | number | null)[] | null>(null);
  readonly textoVolver = input('Volver');
}
