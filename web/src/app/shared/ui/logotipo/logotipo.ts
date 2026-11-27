import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Logotipo de BanGus: la marca (una "B" de columna y dos bloques, sin cuadro de fondo) + "BanGus" en serif.
 * Proporciones: alto de la marca = 1.25 × texto; separación = 0.18 × texto; centrados verticalmente.
 * La marca se dibuja en línea con `currentColor` (negro sobre claro, blanco dentro de las zonas de marca).
 * Tamaño mínimo: 20 px de texto.
 */
@Component({
  selector: 'app-logotipo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'logotipo',
    '[class.logotipo--grande]': "tamano() === 'grande'",
    '[class.logotipo--oscuro]': "fondo() === 'oscuro'",
  },
  styles: `
    :host {
      --logo-texto: 20px;
      display: inline-flex;
      gap: calc(var(--logo-texto) * 0.18);
      align-items: center;
      color: var(--texto);
      white-space: nowrap;
    }
    :host(.logotipo--grande) {
      --logo-texto: 28px;
    }
    svg {
      display: block;
      flex: none;
      width: calc(var(--logo-texto) * 1.25 * 32 / 40);
      height: calc(var(--logo-texto) * 1.25);
      fill: currentColor;
    }
    span {
      font-family: var(--serif);
      font-size: var(--logo-texto);
      font-weight: 600;
      line-height: 1;
      letter-spacing: -0.015em;
    }
  `,
  // viewBox recortado a la marca (x 16–48, y 12–52) para que su alto sea exactamente 1.25 × texto.
  template: `
    <svg viewBox="16 12 32 40" aria-hidden="true" focusable="false">
      <path d="M16 12h9v40h-9ZM28 12h5a9.5 9.5 0 0 1 0 19h-5ZM28 33h10a9.5 9.5 0 0 1 0 19H28Z" />
    </svg>
    <span>BanGus</span>
  `,
})
export class Logotipo {
  readonly fondo = input<'claro' | 'oscuro'>('claro');
  readonly tamano = input<'normal' | 'grande'>('normal');
}
