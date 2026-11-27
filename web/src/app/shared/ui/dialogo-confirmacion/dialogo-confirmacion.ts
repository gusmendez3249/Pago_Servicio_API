import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnDestroy,
  afterNextRender,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';

const FOCALIZABLES = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Diálogo modal de confirmación (p. ej. cerrar sesión). Accesible: `role="dialog"` con `aria-modal`, título
 * enlazado, foco inicial en "Cancelar", foco atrapado, Escape y clic en el velo cancelan (salvo mientras procesa)
 * y al cerrarse el foco vuelve al control que lo abrió. `peligro` pinta el botón de confirmar en rojo: solo para
 * acciones destructivas.
 */
@Component({
  selector: 'app-dialogo-confirmacion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(keydown)': 'alTeclear($event)', '(click)': 'alClicVelo($event)' },
  styles: `
    :host {
      position: fixed;
      inset: 0;
      z-index: 60;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--s-4);
      background: var(--velo);
    }
    .dialogo {
      width: 100%;
      max-width: 420px;
      padding: var(--s-5);
      color: var(--texto);
      background: var(--superficie);
      border: 1px solid var(--borde);
      border-radius: var(--r-tarjeta);
      box-shadow: var(--sombra-modal);
    }
    .dialogo__titulo {
      margin: 0;
      font-family: var(--serif);
      font-size: var(--t-20);
      font-weight: 600;
      line-height: 1.25;
    }
    .dialogo__texto {
      margin: var(--s-2) 0 var(--s-5);
      color: var(--texto-2);
    }
    .dialogo__acciones {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--s-3);
    }
    .dialogo__acciones .btn {
      width: 100%;
      padding: 0 var(--s-4);
    }
    /* Apilados en pantallas muy estrechas, la acción destructiva arriba */
    @media (max-width: 24.99rem) {
      .dialogo__acciones {
        grid-template-columns: 1fr;
      }
      .dialogo__confirmar {
        order: -1;
      }
    }
  `,
  template: `
    <div class="dialogo" role="dialog" aria-modal="true" aria-labelledby="dialogo-titulo" aria-describedby="dialogo-texto" #dialogo>
      <h2 class="dialogo__titulo" id="dialogo-titulo">{{ titulo() }}</h2>
      <p class="dialogo__texto" id="dialogo-texto">{{ texto() }}</p>
      <div class="dialogo__acciones">
        <button #cancelarBtn type="button" class="btn btn--sec" [disabled]="procesando()" (click)="cancelar.emit()">
          Cancelar
        </button>
        <button
          type="button"
          class="btn dialogo__confirmar"
          [class.btn--peligro]="peligro()"
          [disabled]="procesando()"
          [attr.aria-busy]="procesando()"
          (click)="alConfirmar()"
        >
          @if (procesando()) {
            <span class="spinner" aria-hidden="true"></span>
            {{ textoProcesando() }}
          } @else {
            {{ textoConfirmar() }}
          }
        </button>
      </div>
    </div>
  `,
})
export class DialogoConfirmacion implements OnDestroy {
  readonly titulo = input.required<string>();
  readonly texto = input('');
  readonly textoConfirmar = input('Confirmar');
  readonly textoProcesando = input('Procesando…');
  readonly peligro = input(false);
  readonly procesando = input(false);
  readonly confirmar = output<void>();
  readonly cancelar = output<void>();

  private readonly dialogo = viewChild.required<ElementRef<HTMLElement>>('dialogo');
  private readonly cancelarBtn = viewChild.required<ElementRef<HTMLButtonElement>>('cancelarBtn');
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly previo = typeof document !== 'undefined' ? (document.activeElement as HTMLElement | null) : null;

  constructor() {
    afterNextRender(() => this.cancelarBtn().nativeElement.focus(), { injector: inject(Injector) });
  }

  ngOnDestroy(): void {
    this.previo?.focus?.();
  }

  protected alConfirmar(): void {
    if (this.procesando()) return;
    this.confirmar.emit();
  }

  /** Clic fuera de la tarjeta (sobre el velo) cancela. */
  protected alClicVelo(evento: MouseEvent): void {
    if (evento.target === this.anfitrion.nativeElement && !this.procesando()) this.cancelar.emit();
  }

  protected alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      if (!this.procesando()) this.cancelar.emit();
      return;
    }
    if (evento.key !== 'Tab') return;
    const elementos = Array.from(this.dialogo().nativeElement.querySelectorAll<HTMLElement>(FOCALIZABLES));
    if (elementos.length === 0) {
      evento.preventDefault();
      return;
    }
    const primero = elementos[0];
    const ultimo = elementos[elementos.length - 1];
    const activo = document.activeElement;
    if (evento.shiftKey && (activo === primero || !this.anfitrion.nativeElement.contains(activo))) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && (activo === ultimo || !this.anfitrion.nativeElement.contains(activo))) {
      evento.preventDefault();
      primero.focus();
    }
  }
}
