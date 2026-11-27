import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnDestroy,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { esPinValido, soloDigitos } from '../../domain/models/pago-reglas';

const FOCALIZABLES = 'button:not([disabled]), input:not([disabled]), [href], select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Diálogo de confirmación con PIN. El PIN vive solo en el estado local de este componente y se borra tras
 * cada intento. Accesible: `role="dialog"`, `aria-modal`, foco atrapado y devuelto, Escape solo sin procesar.
 */
@Component({
  selector: 'app-modal-confirmacion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      position: fixed;
      inset: 0;
      z-index: 50;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--s-4);
      background: var(--velo);
    }
    .modal {
      width: 100%;
      max-width: 420px;
      max-height: 100%;
      overflow: auto;
      padding: var(--s-6) var(--s-5) var(--s-5);
      background: var(--superficie);
      border-radius: var(--r-tarjeta);
      border: 1px solid var(--borde);
      box-shadow: var(--sombra-modal);
      animation: subir 0.2s ease-out;
    }
    .modal__etiqueta {
      margin: var(--s-4) 0 var(--s-1);
      color: var(--texto-2);
      font-size: var(--t-14);
    }
    .modal__total {
      margin: 0 0 var(--s-5);
    }
    .modal__pin {
      height: 64px;
      padding-left: calc(var(--s-4) + 0.5em);
      font-size: var(--t-28);
      font-weight: 600;
      letter-spacing: 0.5em;
      text-align: center;
    }
    .modal__estado {
      display: flex;
      gap: var(--s-2);
      align-items: center;
      margin: 0 0 var(--s-3);
      color: var(--texto-2);
    }
    .modal__aviso {
      margin-top: 0;
    }
    .acciones {
      flex-direction: column;
    }
    .acciones > .btn {
      width: 100%;
    }
    @keyframes subir {
      from {
        opacity: 0;
        transform: translateY(16px);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .modal {
        animation: none;
      }
    }
    @media (max-width: 37.49rem) {
      :host {
        align-items: flex-end;
        padding: 0;
      }
      .modal {
        max-width: none;
        padding-bottom: calc(var(--s-5) + env(safe-area-inset-bottom, 0px));
        border-radius: var(--r-tarjeta) var(--r-tarjeta) 0 0;
      }
    }
  `,
  host: { '(keydown)': 'alTeclear($event)' },
  template: `
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-titulo" aria-describedby="modal-total" #dialogo>
      <h2 id="modal-titulo">Confirma tu pago</h2>
      <p class="modal__etiqueta">Vas a pagar</p>
      <p class="modal__total cifra cifra--40" id="modal-total">{{ moneda(total()) }}</p>
      <form (submit)="enviar($event)" novalidate>
        <div class="campo">
          <label class="campo__etiqueta" for="pago-pin">PIN de 4 dígitos</label>
          <input
            #campo
            id="pago-pin"
            placeholder=" "
            class="campo__input modal__pin"
            type="password"
            inputmode="numeric"
            maxlength="4"
            autocomplete="off"
            [value]="pin()"
            [disabled]="procesando()"
            [attr.aria-invalid]="aviso() ? 'true' : null"
            [attr.aria-describedby]="aviso() ? 'pago-pin-aviso' : null"
            (input)="alEscribir($any($event.target).value)"
          />
        </div>
        <div aria-live="assertive">
          @if (aviso(); as a) {
            <p class="campo__error modal__aviso" id="pago-pin-aviso">{{ a }}</p>
          }
        </div>
        @if (procesando()) {
          <p class="modal__estado" role="status"><span class="spinner" aria-hidden="true"></span> Procesando tu pago…</p>
        }
        <div class="acciones">
          <button type="submit" class="btn" [disabled]="procesando() || !pinValido()">
            {{ procesando() ? 'Procesando…' : 'Confirmar pago' }}
          </button>
          <button type="button" class="btn btn--sec" [disabled]="procesando()" (click)="cancelar.emit()">
            Cancelar
          </button>
        </div>
      </form>
    </div>
  `,
})
export class ModalConfirmacion implements OnDestroy {
  readonly total = input.required<number>();
  readonly procesando = input(false);
  readonly aviso = input<string | null>(null);
  readonly confirmar = output<string>();
  readonly cancelar = output<void>();

  protected readonly pin = signal('');
  protected readonly pinValido = () => esPinValido(this.pin());

  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');
  private readonly dialogo = viewChild<ElementRef<HTMLElement>>('dialogo');
  private readonly injector = inject(Injector);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly previo = typeof document !== 'undefined' ? (document.activeElement as HTMLElement | null) : null;

  constructor() {
    // Foco al campo al abrir y cada vez que termina un intento.
    effect(() => {
      if (this.procesando()) return;
      afterNextRender(() => this.campo()?.nativeElement.focus(), { injector: this.injector });
    });
  }

  ngOnDestroy(): void {
    this.pin.set('');
    this.previo?.focus?.();
  }

  protected moneda(valor: number): string {
    return formatoMoneda(valor);
  }

  protected alEscribir(valor: string): void {
    this.pin.set(soloDigitos(valor).slice(0, 4));
    const el = this.campo()?.nativeElement;
    if (el && el.value !== this.pin()) el.value = this.pin();
  }

  protected enviar(evento: Event): void {
    evento.preventDefault();
    if (this.procesando() || !this.pinValido()) return;
    const pin = this.pin();
    // El PIN se borra del campo en el mismo instante en que sale la petición.
    this.pin.set('');
    const el = this.campo()?.nativeElement;
    if (el) el.value = '';
    this.confirmar.emit(pin);
  }

  protected alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      if (!this.procesando()) this.cancelar.emit();
      return;
    }
    if (evento.key !== 'Tab') return;
    const elementos = Array.from(this.dialogo()?.nativeElement.querySelectorAll<HTMLElement>(FOCALIZABLES) ?? []);
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
