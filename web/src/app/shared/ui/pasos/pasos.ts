import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

type EstadoPaso = 'hecho' | 'actual' | 'pendiente';

/** Indicador "Paso 2 de 3" + nombre del paso y barra de tres segmentos (el actual en azul corporativo): Servicio, Referencia (o Teléfono) y Confirmación. */
@Component({
  selector: 'app-pasos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pasos.scss',
  template: `
    <nav [attr.aria-label]="etiqueta()">
      <p class="pasos__texto">
        <span class="pasos__numero">Paso {{ actual() }} de {{ pasos().length }}</span>
        <span class="pasos__nombre">{{ nombreActual() }}</span>
      </p>
      <ol class="pasos" aria-hidden="true" [style.grid-template-columns]="'repeat(' + pasos().length + ', 1fr)'">
        @for (p of pasos(); track p.numero) {
          <li [class]="'paso paso--' + p.estado"></li>
        }
      </ol>
    </nav>
  `,
})
export class Pasos {
  readonly actual = input.required<number>();
  /** Nombres de los pasos; sin ellos se usan los del flujo de pago. */
  readonly nombres = input<readonly string[] | null>(null);
  readonly etiqueta = input('Progreso del pago');
  /** Producto de precio fijo (sin referencia): el segundo paso se llama "Teléfono". */
  readonly directo = input(false);

  private readonly textos = computed(
    () => this.nombres() ?? ['Servicio', this.directo() ? 'Teléfono' : 'Referencia', 'Confirmación'],
  );
  protected readonly nombreActual = computed(() => this.textos()[this.actual() - 1]);

  protected readonly pasos = computed(() => {
    const actual = this.actual();
    return this.textos().map((texto, i) => {
      const numero = i + 1;
      const estado: EstadoPaso = numero < actual ? 'hecho' : numero === actual ? 'actual' : 'pendiente';
      return { numero, texto, estado };
    });
  });
}
