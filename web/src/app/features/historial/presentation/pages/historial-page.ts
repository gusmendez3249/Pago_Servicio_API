import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cabecera } from '../../../../shared/ui/cabecera/cabecera';
import { Icono } from '../../../../shared/ui/icono/icono';
import {
  TEXTO_ERROR_LISTA,
  TEXTO_ERROR_MAS,
  TEXTO_SIN_PAGOS,
  TEXTO_SIN_PAGOS_FILTRO,
  TITULO_HISTORIAL,
} from '../../domain/models/mensajes-historial';
import { ElementoHistorial } from '../../domain/models/historial';
import { formatoFechaHora } from '../../domain/models/historial-reglas';
import { FilaPago } from '../components/fila-pago';
import { HistorialFiltros } from '../components/historial-filtros';
import { HistorialStore } from '../store/historial.store';

interface GrupoFecha {
  clave: string;
  titulo: string;
  elementos: ElementoHistorial[];
}

/** Clave del día local (AAAA-MM-DD) o null si la fecha no se puede interpretar. */
function claveDia(fecha: Date): string {
  return `${fecha.getFullYear()}-${fecha.getMonth() + 1}-${fecha.getDate()}`;
}

/** Movimientos agrupados por día, en el orden en que llegan del store. Solo presentación. */
export function agruparPorFecha(elementos: ElementoHistorial[], hoy = new Date()): GrupoFecha[] {
  const ayer = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - 1);
  const grupos: GrupoFecha[] = [];
  for (const e of elementos) {
    const fecha = e.fechaTransaccion ? new Date(e.fechaTransaccion) : null;
    const valida = fecha !== null && !Number.isNaN(fecha.getTime());
    const clave = valida ? claveDia(fecha) : 'sin-fecha';
    let grupo = grupos.find((g) => g.clave === clave);
    if (!grupo) {
      let titulo = 'Sin fecha';
      if (valida) {
        if (clave === claveDia(hoy)) titulo = 'Hoy';
        else if (clave === claveDia(ayer)) titulo = 'Ayer';
        else titulo = (formatoFechaHora(e.fechaTransaccion) ?? '').split(', ')[0];
      }
      grupo = { clave, titulo, elementos: [] };
      grupos.push(grupo);
    }
    grupo.elementos.push(e);
  }
  return grupos;
}

/** Lista de pagos del cliente agrupada por día. Siempre se pide al backend al entrar. */
@Component({
  selector: 'app-historial-page',
  imports: [Cabecera, FilaPago, HistorialFiltros, Icono, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .historial__cabecera {
      display: flex;
      flex-wrap: wrap;
      gap: var(--s-3);
      align-items: center;
      justify-content: space-between;
    }
    .historial__actualizar {
      min-height: 44px;
      padding: 0 var(--s-5);
      font-size: var(--t-14);
    }
    .historial__grupo + .historial__grupo {
      margin-top: var(--s-5);
    }
    .historial__fecha {
      margin: 0 0 var(--s-2);
      padding: 0 var(--s-1);
      color: var(--texto-2);
      font-size: var(--t-14);
      font-weight: 600;
    }
    .historial__mas {
      display: flex;
      flex-direction: column;
      gap: var(--s-3);
      align-items: center;
      margin-top: var(--s-5);
    }
    .historial__mas p {
      margin: 0;
    }
  `,
  template: `
    <app-cabecera />
    <main>
      <section class="franja oscuro" aria-labelledby="historial-titulo">
        <div class="franja__contenido">
          <div class="historial__cabecera">
            <h1 id="historial-titulo">{{ titulo }}</h1>
            <button
              type="button"
              class="btn btn--contorno-claro historial__actualizar"
              [disabled]="store.estado() === 'cargando'"
              (click)="store.cargar()"
            >
              Actualizar
            </button>
          </div>
          <app-historial-filtros [seleccionado]="store.filtro()" (cambia)="store.seleccionarFiltro($event)" />
        </div>
      </section>

      <div class="hoja">
        <div class="hoja__contenido"><div class="columna-8">
          @switch (store.estado()) {
            @case ('cargando') {
              <ul class="lista" role="status" aria-live="polite" aria-label="Cargando tus pagos">
                @for (n of esqueletos; track n) {
                  <li class="esqueleto" aria-hidden="true">
                    <span class="esqueleto__logo"></span>
                    <span class="esqueleto__lineas"><span></span><span></span></span>
                  </li>
                }
              </ul>
            }
            @case ('error') {
              <section class="estado-error" role="alert">
                <span class="estado-error__icono"><app-icono nombre="alerta" /></span>
                <p class="estado-error__texto">{{ textoError }}</p>
                <button type="button" class="btn" (click)="store.cargar()">Reintentar</button>
              </section>
            }
            @case ('vacio') {
              <section class="mensaje" role="status">
                <span class="mensaje__icono"><app-icono nombre="pagos" /></span>
                <p>{{ store.filtro() === 'todos' ? sinPagos : sinPagosFiltro }}</p>
                @if (store.filtro() === 'todos') {
                  <a class="btn" routerLink="/catalogo">Ir al catálogo</a>
                }
              </section>
            }
            @default {
              @for (g of grupos(); track g.clave) {
                <section class="historial__grupo" [attr.aria-label]="g.titulo">
                  <h2 class="historial__fecha">{{ g.titulo }}</h2>
                  <ul class="lista">
                    @for (e of g.elementos; track e.upc ?? $index) {
                      <li><app-fila-pago [pago]="e" /></li>
                    }
                  </ul>
                </section>
              }
              @if (store.hayMas() || store.errorMas()) {
                <div class="historial__mas">
                  @if (store.errorMas()) {
                    <p class="campo__error" role="alert">{{ textoErrorMas }}</p>
                  }
                  <button type="button" class="btn btn--sec" [disabled]="store.cargandoMas()" (click)="store.verMas()">
                    {{ store.cargandoMas() ? 'Cargando…' : 'Ver más' }}
                  </button>
                </div>
              }
            }
          }
        </div></div>
      </div>
    </main>
  `,
})
export class HistorialPage {
  protected readonly store = inject(HistorialStore);
  protected readonly titulo = TITULO_HISTORIAL;
  protected readonly sinPagos = TEXTO_SIN_PAGOS;
  protected readonly sinPagosFiltro = TEXTO_SIN_PAGOS_FILTRO;
  protected readonly textoError = TEXTO_ERROR_LISTA;
  protected readonly textoErrorMas = TEXTO_ERROR_MAS;
  protected readonly esqueletos = [1, 2, 3, 4, 5, 6];
  protected readonly grupos = computed(() => agruparPorFecha(this.store.elementos()));

  constructor() {
    // Siempre se pide al entrar, con la primera pestaña: un pago hecho en otra plataforma aparece aquí.
    this.store.limpiar();
    void this.store.cargar();
  }
}
