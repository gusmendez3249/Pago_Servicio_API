import { Entrada } from '../../../../shared/entrada/entrada.directive';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { mensajeGenerico } from '../../../../core/error/mensajes-error';
import { nuevaIdempotencyKey } from '../../../../core/http/idempotency';
import { Failure } from '../../../../core/http/result';
import { Cabecera } from '../../../../shared/ui/cabecera/cabecera';
import { DialogoConfirmacion } from '../../../../shared/ui/dialogo-confirmacion/dialogo-confirmacion';
import { Icono } from '../../../../shared/ui/icono/icono';
import { AuthStore } from '../../../auth/presentation/store/auth.store';
import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { AdminApi, CuentaAdmin, Recarga } from '../../data/admin-api';

const MONTO_MAXIMO = 50_000;

/** Texto amigable de un fallo del panel; nunca el mensaje del servidor. */
export function mensajeAdmin(falla: Failure): string {
  const motivo = (falla.detalle as { motivo?: string } | undefined)?.motivo;
  if (motivo === 'TOPE_DIARIO_CUENTA') {
    return 'Esta cuenta ya llegó al tope de recargas de hoy. Podrás recargarla de nuevo mañana.';
  }
  if (motivo === 'TOPE_DIARIO_EJECUTIVO') {
    return 'Llegaste a tu tope de recargas de hoy. Podrás seguir recargando mañana.';
  }
  switch (falla.tipo) {
    case 'no-encontrado':
      return 'No existe una cuenta con ese número. Revísalo con el cliente.';
    case 'prohibido':
      return 'La cuenta o su titular están inactivos: no se puede recargar.';
    default:
      return falla.httpStatus === 409
        ? 'Esta recarga ya se estaba procesando. Revisa las recargas recientes antes de repetirla.'
        : mensajeGenerico(falla);
  }
}

/**
 * Panel de administrador: hoy la única forma de abonar saldo a una cuenta (las cuentas nuevas nacen en $0.00).
 * Flujo: buscar la cuenta por su número, confirmar titular y monto, y recargar. Cada recarga lleva su propia
 * Idempotency-Key: si la red falla y se reintenta, el servidor no abona dos veces.
 */
@Component({
  selector: 'app-admin-page',
  imports: [Cabecera, DialogoConfirmacion, Icono, ReactiveFormsModule, Entrada],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .admin__seccion + .admin__seccion {
      margin-top: var(--s-6);
    }
    .admin__seccion > h2 {
      margin-bottom: var(--s-3);
    }
    .admin__busqueda {
      display: grid;
      gap: 0 var(--s-3);
      align-items: start;
    }
    .admin__busqueda .btn {
      height: 56px;
    }
    @media (min-width: 37.5rem) {
      .admin__busqueda {
        grid-template-columns: 1fr auto;
      }
    }
    .admin__datos {
      margin: 0 0 var(--s-5);
    }
    .admin__datos > div {
      display: flex;
      gap: var(--s-4);
      justify-content: space-between;
      padding: var(--s-3) 0;
      border-bottom: 1px solid var(--borde);
    }
    .admin__datos dt {
      color: var(--texto-2);
    }
    .admin__datos dd {
      margin: 0;
      font-weight: 500;
      text-align: right;
    }
    .admin__mas {
      margin-top: var(--s-4);
    }
  `,
  template: `
    <app-cabecera />
    <main>
      <section class="franja oscuro" aria-labelledby="admin-titulo">
        <div class="franja__contenido">
          <h1 id="admin-titulo">Recargas de saldo</h1>
          <p class="franja__sub">Abona dinero a la cuenta de un cliente. Las cuentas nuevas inician en $0.00.</p>
        </div>
      </section>

      <div class="hoja">
        <div class="hoja__contenido">
          <div class="rejilla-8-4">
            <div>
              <section class="admin__seccion" aria-labelledby="admin-buscar">
                <h2 id="admin-buscar">Cuenta del cliente</h2>
                <form class="admin__busqueda" [formGroup]="busqueda" (ngSubmit)="buscar()" novalidate>
                  <div class="campo">
                    <label class="campo__etiqueta" for="numeroCuenta">Número de cuenta (12 dígitos)</label>
                    <input
                      id="numeroCuenta" appEntrada="digitos"
                      class="campo__input"
                      [formControl]="numeroCuenta"
                      placeholder=" "
                      inputmode="numeric"
                      maxlength="12"
                      autocomplete="off"
                      [attr.aria-invalid]="errorNumero() ? 'true' : null"
                      [attr.aria-describedby]="errorNumero() ? 'numero-error' : null"
                      (input)="errorBusqueda.set(null)"
                    />
                    <p class="campo__error" id="numero-error">{{ errorNumero() }}</p>
                  </div>
                  <button class="btn" type="submit" [disabled]="buscando()">
                    @if (buscando()) {
                      <span class="spinner" aria-hidden="true"></span> Buscando…
                    } @else {
                      <app-icono nombre="buscar" /> Buscar cuenta
                    }
                  </button>
                </form>

                <div aria-live="polite" role="status">
                  @if (errorBusqueda(); as e) {
                    <div class="aviso aviso--error">
                      <app-icono class="aviso__icono" nombre="alerta" />
                      <div class="aviso__cuerpo"><p>{{ e }}</p></div>
                    </div>
                  }
                  @if (ultima(); as r) {
                    <div class="aviso aviso--ok">
                      <app-icono class="aviso__icono" nombre="check" />
                      <div class="aviso__cuerpo">
                        <p class="aviso__titulo">Recarga aplicada</p>
                        <p>Se abonaron {{ moneda(r.monto) }} a la cuenta {{ r.numCuentaEnmascarada }}. Nuevo saldo:
                          {{ moneda(r.saldoResultante) }}. Folio {{ r.folio }}.</p>
                      </div>
                    </div>
                  }
                </div>
              </section>

              @if (cuenta(); as c) {
                <section class="admin__seccion tarjeta" aria-labelledby="admin-cuenta">
                  <h2 id="admin-cuenta" class="tarjeta__titulo">{{ c.titular }}</h2>
                  <dl class="admin__datos">
                    <div><dt>Cuenta</dt><dd class="cifra">{{ c.numCuentaEnmascarada }}</dd></div>
                    <div><dt>Saldo actual</dt><dd class="cifra">{{ moneda(c.saldo) }}</dd></div>
                    <div><dt>Estatus</dt><dd>{{ c.recargable ? 'Activa' : 'Inactiva' }}</dd></div>
                  </dl>

                  @if (c.recargable) {
                    <form [formGroup]="recarga" (ngSubmit)="pedirConfirmacion()" novalidate>
                      <div class="campo">
                        <label class="campo__etiqueta" for="monto">Monto a recargar (MXN)</label>
                        <input
                          id="monto" appEntrada="importe" [enteros]="5" [valorMaximo]="50000"
                          class="campo__input campo__input--grande"
                          [formControl]="monto"
                          placeholder=" "
                          inputmode="decimal"
                          autocomplete="off"
                          [attr.aria-invalid]="errorMonto() ? 'true' : null"
                          [attr.aria-describedby]="errorMonto() ? 'monto-error' : 'monto-ayuda'"
                          (input)="errorRecarga.set(null)"
                        />
                        <p class="campo__ayuda" id="monto-ayuda">De $1.00 a $50,000.00 por recarga.</p>
                        <p class="campo__error" id="monto-error">{{ errorMonto() }}</p>
                      </div>
                      <div aria-live="assertive" role="alert">
                        @if (errorRecarga(); as e) {
                          <div class="aviso aviso--error">
                            <app-icono class="aviso__icono" nombre="alerta" />
                            <div class="aviso__cuerpo"><p>{{ e }}</p></div>
                          </div>
                        }
                      </div>
                      <button class="btn btn--bloque" type="submit">Recargar</button>
                    </form>
                  } @else {
                    <div class="aviso aviso--error">
                      <app-icono class="aviso__icono" nombre="alerta" />
                      <div class="aviso__cuerpo"><p>La cuenta o su titular están inactivos: no se puede recargar.</p></div>
                    </div>
                  }
                </section>
              }
            </div>

            <aside class="rejilla__lado">
              <section class="admin__seccion" aria-labelledby="admin-recientes">
                <h2 id="admin-recientes">Recargas recientes</h2>
                @if (errorHistorial()) {
                  <section class="estado-error" role="alert">
                    <span class="estado-error__icono"><app-icono nombre="alerta" /></span>
                    <p class="estado-error__texto">No pudimos cargar las recargas.</p>
                    <button type="button" class="btn" (click)="cargarHistorial(0)">Reintentar</button>
                  </section>
                } @else if (recargas().length === 0) {
                  <p class="mensaje">Aún no hay recargas. La primera que hagas aparecerá aquí.</p>
                } @else {
                  <ul class="lista">
                    @for (r of recargas(); track r.folio) {
                      <li class="renglon">
                        <span class="renglon__texto">
                          <span class="renglon__titulo">{{ r.titular }}</span>
                          <span class="renglon__sub">{{ r.numCuentaEnmascarada }}, {{ fecha(r.fecha) }}</span>
                        </span>
                        <span class="renglon__monto">{{ moneda(r.monto) }}</span>
                      </li>
                    }
                  </ul>
                  @if (pagina() + 1 < totalPaginas()) {
                    <button type="button" class="btn btn--sec admin__mas" (click)="cargarHistorial(pagina() + 1)">
                      Ver más recargas
                    </button>
                  }
                }
              </section>

              <section class="admin__seccion" aria-labelledby="admin-sesion">
                <h2 id="admin-sesion">Sesión</h2>
                <button type="button" class="btn btn--sec" (click)="confirmandoSalida.set(true)">
                  <app-icono nombre="salir" /> Cerrar sesión
                </button>
              </section>
            </aside>
          </div>
        </div>
      </div>
    </main>

    @if (confirmando() && cuenta(); as c) {
      <app-dialogo-confirmacion
        [titulo]="'¿Recargar ' + moneda(montoNumero()) + '?'"
        [texto]="'Se abonarán ' + moneda(montoNumero()) + ' a la cuenta ' + c.numCuentaEnmascarada + ' de ' + c.titular + '. Esta acción no se puede deshacer.'"
        textoConfirmar="Recargar"
        textoProcesando="Recargando…"
        [procesando]="recargando()"
        (confirmar)="recargar()"
        (cancelar)="confirmando.set(false)"
      />
    }
    @if (confirmandoSalida()) {
      <app-dialogo-confirmacion
        titulo="¿Cerrar sesión?"
        texto="Tendrás que volver a iniciar sesión para hacer recargas."
        textoConfirmar="Cerrar sesión"
        textoProcesando="Cerrando…"
        [peligro]="true"
        [procesando]="cerrando()"
        (confirmar)="cerrarSesion()"
        (cancelar)="confirmandoSalida.set(false)"
      />
    }
  `,
})
export class AdminPage {
  private readonly api = inject(AdminApi);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly numeroCuenta = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^\d{12}$/)],
  });
  protected readonly monto = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^\d{1,5}(\.\d{1,2})?$/)],
  });

  /** Grupos de los dos formularios: sin [formGroup], Enter haría un envío nativo y recargaría la página. */
  protected readonly busqueda = new FormGroup({ numeroCuenta: this.numeroCuenta });
  protected readonly recarga = new FormGroup({ monto: this.monto });

  protected readonly buscando = signal(false);
  protected readonly cuenta = signal<CuentaAdmin | null>(null);
  /** Número completo de la cuenta encontrada (solo en memoria; en pantalla va enmascarado). */
  private numeroEncontrado = '';
  protected readonly errorBusqueda = signal<string | null>(null);
  protected readonly confirmando = signal(false);
  protected readonly recargando = signal(false);
  protected readonly errorRecarga = signal<string | null>(null);
  protected readonly ultima = signal<Recarga | null>(null);
  /** Llave de la recarga en curso: se conserva entre reintentos y se descarta al terminar. */
  private llave: string | null = null;

  protected readonly recargas = signal<Recarga[]>([]);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly errorHistorial = signal(false);

  protected readonly confirmandoSalida = signal(false);
  protected readonly cerrando = signal(false);

  constructor() {
    void this.cargarHistorial(0);
  }

  protected errorNumero(): string | null {
    const c = this.numeroCuenta;
    if (!c.touched || !c.errors) return null;
    return c.errors['required'] ? 'Escribe el número de cuenta.' : 'El número de cuenta tiene 12 dígitos.';
  }

  protected errorMonto(): string | null {
    const c = this.monto;
    if (!c.touched) return null;
    if (c.errors?.['required']) return 'Escribe el monto.';
    if (c.errors?.['pattern']) return 'Escribe solo números, con hasta 2 decimales.';
    const n = this.montoNumero();
    if (n < 1) return 'El monto mínimo es $1.00.';
    return n > MONTO_MAXIMO ? 'El monto máximo por recarga es $50,000.00.' : null;
  }

  protected montoNumero(): number {
    return Number(this.monto.value);
  }

  protected async buscar(): Promise<void> {
    this.numeroCuenta.markAsTouched();
    if (this.numeroCuenta.invalid || this.buscando()) return;
    this.buscando.set(true);
    this.errorBusqueda.set(null);
    this.ultima.set(null);
    const numero = this.numeroCuenta.value;
    const r = await this.api.consultarCuenta(numero);
    this.buscando.set(false);
    if (r.ok) {
      this.numeroEncontrado = numero;
      this.cuenta.set(r.data);
      this.monto.reset('');
      this.errorRecarga.set(null);
    } else {
      this.cuenta.set(null);
      this.errorBusqueda.set(mensajeAdmin(r));
    }
  }

  protected pedirConfirmacion(): void {
    this.monto.markAsTouched();
    if (this.errorMonto()) return;
    this.llave ??= nuevaIdempotencyKey();
    this.confirmando.set(true);
  }

  protected async recargar(): Promise<void> {
    if (this.recargando() || this.llave === null) return;
    this.recargando.set(true);
    const r = await this.api.recargar(this.numeroEncontrado, this.montoNumero(), this.llave);
    this.recargando.set(false);
    this.confirmando.set(false);
    if (r.ok) {
      this.llave = null;
      this.ultima.set(r.data);
      this.cuenta.update((c) => (c ? { ...c, saldo: r.data.saldoResultante } : c));
      this.monto.reset('');
      void this.cargarHistorial(0);
      return;
    }
    // Ante un fallo de red se conserva la llave: reintentar no abona dos veces
    if (r.tipo !== 'red' && r.tipo !== 'servidor') this.llave = null;
    this.errorRecarga.set(mensajeAdmin(r));
  }

  protected async cargarHistorial(pagina: number): Promise<void> {
    const r = await this.api.historial(pagina);
    if (!r.ok) {
      this.errorHistorial.set(true);
      return;
    }
    this.errorHistorial.set(false);
    this.recargas.set(pagina === 0 ? r.data.recargas : [...this.recargas(), ...r.data.recargas]);
    this.pagina.set(r.data.pagina);
    this.totalPaginas.set(r.data.totalPaginas);
  }

  protected moneda(valor: number): string {
    return formatoMoneda(valor);
  }

  protected fecha(iso: string): string {
    const d = new Date(iso);
    return Number.isNaN(d.getTime())
      ? ''
      : d.toLocaleString('es-MX', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  }

  protected async cerrarSesion(): Promise<void> {
    if (this.cerrando()) return;
    this.cerrando.set(true);
    try {
      await this.auth.cerrarSesion();
    } finally {
      this.cerrando.set(false);
      await this.router.navigate(['/login']);
    }
  }
}
