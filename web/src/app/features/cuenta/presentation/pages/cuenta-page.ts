import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SessionStore } from '../../../../core/session/session-store';
import { Cabecera } from '../../../../shared/ui/cabecera/cabecera';
import { DialogoConfirmacion } from '../../../../shared/ui/dialogo-confirmacion/dialogo-confirmacion';
import { Icono } from '../../../../shared/ui/icono/icono';
import { AuthStore } from '../../../auth/presentation/store/auth.store';
import { formatoMoneda } from '../../../catalogo/domain/models/producto-reglas';
import { CuentaCliente } from '../../../pago/domain/models/pago';
import { ObtenerCuentasUseCase } from '../../../pago/domain/use-cases/pago.use-cases';

type EstadoCuentas = 'cargando' | 'listo' | 'error';

/**
 * Pantalla "Cuenta": nombre del cliente, sus cuentas (mismo caso de uso que el pago) y cierre de sesión con
 * confirmación. Solo presentación: no agrega reglas ni contratos nuevos.
 */
@Component({
  selector: 'app-cuenta-page',
  imports: [Cabecera, DialogoConfirmacion, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .cuenta__seccion + .cuenta__seccion {
      margin-top: var(--s-6);
    }
    .cuenta__seccion > h2 {
      margin-bottom: var(--s-3);
    }
  `,
  template: `
    <app-cabecera />
    <main>
      <section class="franja oscuro" aria-labelledby="cuenta-titulo">
        <div class="franja__contenido">
          <h1 id="cuenta-titulo">Tu cuenta</h1>
          <p class="franja__sub">{{ sesion.nombreCliente() }}</p>
        </div>
      </section>

      <div class="hoja">
        <div class="hoja__contenido"><div class="columna-8">
          <section class="cuenta__seccion" aria-labelledby="cuenta-cuentas">
            <h2 id="cuenta-cuentas">Cuentas de cargo</h2>
            @switch (estado()) {
              @case ('cargando') {
                <ul class="lista" role="status" aria-label="Cargando tus cuentas">
                  @for (n of [1, 2]; track n) {
                    <li class="esqueleto" aria-hidden="true">
                      <span class="esqueleto__lineas"><span></span><span></span></span>
                    </li>
                  }
                </ul>
              }
              @case ('error') {
                <section class="estado-error" role="alert">
                  <span class="estado-error__icono"><app-icono nombre="alerta" /></span>
                  <p class="estado-error__texto">No pudimos cargar tus cuentas. Inténtalo de nuevo.</p>
                  <button type="button" class="btn" (click)="cargar()">Reintentar</button>
                </section>
              }
              @default {
                @if (cuentas().length === 0) {
                  <p class="mensaje">Aún no tienes cuentas activas para pagar.</p>
                } @else {
                  <ul class="lista">
                    @for (c of cuentas(); track c.numCuentaEnmascarada) {
                      <li class="renglon">
                        <span class="renglon__texto">
                          <span class="renglon__titulo">Cuenta {{ c.numCuentaEnmascarada }}</span>
                          <span class="renglon__sub">Saldo disponible</span>
                        </span>
                        <span class="renglon__monto">{{ moneda(c.saldo) }}</span>
                      </li>
                    }
                  </ul>
                }
              }
            }
          </section>

          <section class="cuenta__seccion" aria-labelledby="cuenta-sesion">
            <h2 id="cuenta-sesion">Sesión</h2>
            <button type="button" class="btn btn--sec" (click)="confirmando.set(true)">
              <app-icono nombre="salir" /> Cerrar sesión
            </button>
          </section>
        </div></div>
      </div>
    </main>
    @if (confirmando()) {
      <app-dialogo-confirmacion
        titulo="¿Cerrar sesión?"
        texto="Tendrás que volver a iniciar sesión para pagar tus servicios."
        textoConfirmar="Cerrar sesión"
        textoProcesando="Cerrando…"
        [peligro]="true"
        [procesando]="cerrando()"
        (confirmar)="cerrarSesion()"
        (cancelar)="confirmando.set(false)"
      />
    }
  `,
})
export class CuentaPage {
  protected readonly sesion = inject(SessionStore);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly obtenerCuentas = inject(ObtenerCuentasUseCase);

  protected readonly estado = signal<EstadoCuentas>('cargando');
  protected readonly cuentas = signal<CuentaCliente[]>([]);
  protected readonly confirmando = signal(false);
  protected readonly cerrando = signal(false);

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.estado.set('cargando');
    const salida = await this.obtenerCuentas.ejecutar();
    if (salida.tipo === 'cuentas') {
      this.cuentas.set(salida.cuentas);
      this.estado.set('listo');
    } else {
      this.estado.set('error');
    }
  }

  protected moneda(valor: number): string {
    return formatoMoneda(valor);
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
