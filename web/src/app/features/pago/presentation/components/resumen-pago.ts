import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { Icono } from '../../../../shared/ui/icono/icono';
import { textoSaldoInsuficiente } from '../../domain/models/mensajes-pago';
import { PagoStore } from '../store/pago.store';

/** Resumen previo a la confirmación. El importe no se edita aquí. */
@Component({
  selector: 'app-resumen-pago',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .resumen__titulo {
      margin-bottom: var(--s-3);
      font-size: var(--t-16);
    }
    .resumen__cuentas {
      margin: var(--s-5) 0 0;
    }
    .resumen__aviso {
      margin: var(--s-5) 0 0;
    }
    .resumen__total {
      margin: 0 0 var(--s-5);
    }
    .resumen__total-etiqueta {
      display: block;
      margin-bottom: var(--s-1);
      color: var(--texto-2);
      font-size: var(--t-14);
    }
    .resumen__nota {
      margin: var(--s-3) 0 0;
      color: var(--texto-2);
      font-size: var(--t-13);
      text-align: center;
    }
    .resumen__lado .accion-anclada + .btn {
      margin-top: var(--s-3);
    }
  `,
  template: `
    @if (store.producto(); as p) {
      @if (resumen(); as r) {
        <div class="flujo">
          <section class="tarjeta" aria-labelledby="resumen-titulo">
            <h2 id="resumen-titulo" class="resumen__titulo">Detalle del pago</h2>
            <dl class="ficha">
              <dt>Servicio</dt>
              <dd>{{ p.servicio }}</dd>
              <dt>Producto</dt>
              <dd>{{ p.producto }}</dd>
              <dt>{{ store.modo() === 'telefono' ? 'Teléfono' : 'Referencia' }}</dt>
              <dd>{{ store.destinoPago() }}</dd>
              <dt>Importe</dt>
              <dd>{{ moneda(r.total) }}</dd>
              <dt>Cuenta de cargo</dt>
              <dd>{{ r.cuentaEnmascarada }}</dd>
              <dt>Saldo disponible</dt>
              <dd>{{ moneda(r.saldo) }}</dd>
              <dt>Saldo después</dt>
              <dd>{{ r.saldoInsuficiente ? '—' : moneda(r.saldoDespues) }}</dd>
            </dl>
            @if (store.cuentas().length > 1) {
              <div class="campo resumen__cuentas">
                <label class="campo__etiqueta" for="pago-cuenta">Cuenta de cargo</label>
                <select id="pago-cuenta" class="campo__input" (change)="store.seleccionarCuenta($any($event.target).value)">
                  @for (c of store.cuentas(); track c.numCuentaEnmascarada) {
                    <option [value]="c.numCuenta" [selected]="c.numCuenta === store.cuentaElegida()?.numCuenta">
                      {{ c.numCuentaEnmascarada }}
                    </option>
                  }
                </select>
              </div>
            }
            @if (saldoInsuficiente(); as s) {
              <div class="aviso aviso--error resumen__aviso" role="alert">
                <app-icono class="aviso__icono" nombre="alerta" />
                <div class="aviso__cuerpo">
                  <p class="aviso__titulo">{{ s.titulo }}</p>
                  <p>{{ s.cuerpo }}</p>
                </div>
              </div>
            }
          </section>

          <aside class="flujo__lado tarjeta resumen__lado" aria-label="Total a pagar">
            <p class="resumen__total">
              <span class="resumen__total-etiqueta">Total</span>
              <span class="cifra cifra--40">{{ moneda(r.total) }}</span>
            </p>
            <div class="accion-anclada">
              <button type="button" class="btn btn--bloque" [disabled]="r.saldoInsuficiente" (click)="store.pagar()">
                Pagar {{ moneda(r.total) }}
              </button>
            </div>
            <button type="button" class="btn btn--sec btn--bloque" (click)="volver()">
              {{ r.saldoInsuficiente ? 'Volver' : 'Cambiar datos' }}
            </button>
            @if (!r.saldoInsuficiente) {
              <p class="resumen__nota">Te pediremos tu PIN para confirmar.</p>
            }
          </aside>
        </div>
      }
    }
  `,
})
export class ResumenPago {
  protected readonly store = inject(PagoStore);
  private readonly router = inject(Router);

  protected readonly resumen = this.store.resumen;
  protected readonly saldoInsuficiente = computed(() => {
    const r = this.store.resumen();
    return r !== null && r.saldoInsuficiente ? textoSaldoInsuficiente(r.saldo, r.total) : null;
  });

  protected moneda(valor: number): string {
    return formatoMoneda(valor);
  }

  /** "Cambiar datos" / "Volver": vuelve al detalle del producto y descarta todo. */
  protected volver(): void {
    const p = this.store.producto();
    if (p === null) return;
    void this.router.navigate(['/catalogo', p.idSer, p.idProd]);
  }
}
