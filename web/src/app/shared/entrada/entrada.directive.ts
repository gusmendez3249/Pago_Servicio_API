import { Directive, ElementRef, OnDestroy, OnInit, inject, input } from '@angular/core';
import { REGLAS, TipoEntrada, limpiarEntrada } from './reglas-entrada';

const MODO_TECLADO: Partial<Record<TipoEntrada, string>> = {
  digitos: 'numeric',
  importe: 'decimal',
  correo: 'email',
};

/**
 * Restringe lo que se puede escribir o pegar en un campo: `<input appEntrada="curp">`.
 *
 * - `beforeinput`: cada tecla, pegado o arrastre se limpia ANTES de insertarse. Así un texto pegado con guiones o
 *   espacios no lo recorta antes el `maxlength` del navegador.
 * - `input` en fase de captura: red de seguridad para autocompletado y teclados con composición (IME); corre antes
 *   que Angular, así que el formulario nunca ve caracteres prohibidos ni textos más largos de lo permitido.
 */
@Directive({ selector: 'input[appEntrada]' })
export class Entrada implements OnInit, OnDestroy {
  readonly appEntrada = input.required<TipoEntrada>();
  /** Valor máximo de un importe (p. ej. 50000); por encima se conserva el valor anterior. */
  readonly valorMaximo = input<number | undefined>(undefined);
  /** Dígitos enteros de un importe. */
  readonly enteros = input<number | undefined>(undefined);

  private readonly campo = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;
  private anterior = '';
  private readonly antesDeInsertar = (e: Event) => this.insertar(e as InputEvent);
  private readonly alEscribir = () => this.limpiar();

  ngOnInit(): void {
    const tipo = this.appEntrada();
    if (!this.campo.hasAttribute('maxlength')) {
      this.campo.maxLength = REGLAS[tipo].maximo;
    }
    const teclado = MODO_TECLADO[tipo];
    if (teclado && !this.campo.hasAttribute('inputmode')) this.campo.inputMode = teclado;
    if (REGLAS[tipo].mayusculas) this.campo.setAttribute('autocapitalize', 'characters');
    this.anterior = this.campo.value;
    this.campo.addEventListener('beforeinput', this.antesDeInsertar);
    this.campo.addEventListener('input', this.alEscribir, { capture: true });
  }

  ngOnDestroy(): void {
    this.campo.removeEventListener('beforeinput', this.antesDeInsertar);
    this.campo.removeEventListener('input', this.alEscribir, { capture: true });
  }

  private maximo(): number | undefined {
    return this.campo.maxLength > 0 ? this.campo.maxLength : undefined;
  }

  private limpio(valor: string): string {
    return limpiarEntrada(this.appEntrada(), valor, this.maximo(), this.enteros());
  }

  private excedeTope(valor: string): boolean {
    const tope = this.valorMaximo();
    return tope !== undefined && valor !== '' && Number(valor) > tope;
  }

  /** Limpia el texto que se va a insertar y lo inserta ya limpio (o lo bloquea si no queda nada válido). */
  private insertar(e: InputEvent): void {
    if (!e.inputType.startsWith('insert') || e.isComposing) return;
    const texto = e.data ?? e.dataTransfer?.getData('text/plain') ?? null;
    const inicio = this.campo.selectionStart;
    const fin = this.campo.selectionEnd;
    if (texto === null || inicio === null || fin === null) return;

    const actual = this.campo.value;
    const propuesto = actual.slice(0, inicio) + texto + actual.slice(fin);
    const resultado = this.limpio(propuesto);
    if (resultado === propuesto && !this.excedeTope(resultado)) return; // inserción válida: la hace el navegador

    e.preventDefault();
    if (this.excedeTope(resultado) || resultado === actual) return;
    const cursor = Math.min(this.limpio(actual.slice(0, inicio) + texto).length, resultado.length);
    this.campo.value = resultado;
    this.campo.setSelectionRange(cursor, cursor);
    this.campo.dispatchEvent(new Event('input', { bubbles: true }));
  }

  private limpiar(): void {
    const actual = this.campo.value;
    let resultado = this.limpio(actual);
    if (this.excedeTope(resultado)) resultado = this.anterior;
    if (resultado !== actual) {
      const cursor = this.campo.selectionStart ?? actual.length;
      const posicion = Math.min(this.limpio(actual.slice(0, cursor)).length, resultado.length);
      this.campo.value = resultado;
      try {
        this.campo.setSelectionRange(posicion, posicion);
      } catch {
        // Algunos tipos de input no admiten selección: el valor ya quedó limpio
      }
    }
    this.anterior = resultado;
  }
}
