import { Entrada } from '../../../../shared/entrada/entrada.directive';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { textoErrorImporte, textoErrorTelefono } from '../../domain/models/mensajes-pago';
import { PagoStore } from '../store/pago.store';

/** Formulario de captura: teléfono (dos veces) para tipoFront 1 o importe del recibo para SOLO_FORMATO. */
@Component({
  selector: 'app-captura-datos',
  imports: [Entrada],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form (submit)="continuar($event)" novalidate>
      @if (store.modo() === 'telefono') {
        <div class="campo">
          <label class="campo__etiqueta" for="pago-telefono">Número de teléfono</label>
          <input
            id="pago-telefono" appEntrada="digitos"
            placeholder=" "
            class="campo__input campo__input--grande"
            type="tel"
            inputmode="numeric"
            maxlength="10"
            autocomplete="off"
            [value]="store.telefono()"
            [attr.aria-invalid]="errorTelefono() ? 'true' : null"
            [attr.aria-describedby]="errorTelefono() ? 'pago-telefono-error' : null"
            (input)="store.editarTelefono($any($event.target).value)"
          />
        </div>
        <div class="campo">
          <label class="campo__etiqueta" for="pago-telefono-confirmacion">Confirma el número de teléfono</label>
          <input
            id="pago-telefono-confirmacion" appEntrada="digitos"
            placeholder=" "
            class="campo__input campo__input--grande"
            type="tel"
            inputmode="numeric"
            maxlength="10"
            autocomplete="off"
            [value]="store.telefonoConfirmacion()"
            [attr.aria-invalid]="errorTelefono() ? 'true' : null"
            [attr.aria-describedby]="errorTelefono() ? 'pago-telefono-error' : null"
            (input)="store.editarTelefonoConfirmacion($any($event.target).value)"
          />
          @if (errorTelefono(); as e) {
            <p class="campo__error" id="pago-telefono-error" role="alert">{{ e }}</p>
          }
        </div>
      } @else {
        <div class="campo">
          <label class="campo__etiqueta" for="pago-importe">Importe de tu recibo</label>
          <input
            id="pago-importe" appEntrada="importe" [enteros]="5" [valorMaximo]="99999.99"
            placeholder=" "
            class="campo__input campo__input--grande"
            type="text"
            inputmode="decimal"
            autocomplete="off"
            [value]="store.importeTexto()"
            [attr.aria-invalid]="errorImporte() ? 'true' : null"
            [attr.aria-describedby]="errorImporte() ? 'pago-importe-error' : null"
            (input)="store.editarImporte($any($event.target).value)"
          />
          @if (errorImporte(); as e) {
            <p class="campo__error" id="pago-importe-error" role="alert">{{ e }}</p>
          }
        </div>
      }
      <div class="acciones accion-anclada">
        <button type="submit" class="btn">Revisar pago</button>
      </div>
    </form>
  `,
})
export class CapturaDatos {
  protected readonly store = inject(PagoStore);

  protected readonly errorTelefono = computed(() => {
    const e = this.store.errorTelefono();
    return e === null ? null : textoErrorTelefono(e);
  });
  protected readonly errorImporte = computed(() => {
    const e = this.store.errorImporte();
    return e === null ? null : textoErrorImporte(e);
  });

  protected continuar(evento: Event): void {
    evento.preventDefault();
    this.store.continuarCaptura();
  }
}
