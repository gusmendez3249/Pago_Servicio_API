import { Injectable, effect, inject, signal } from '@angular/core';
import { SessionStore } from '../../../../core/session/session-store';
import { ElementoHistorial, EstatusFiltro, FiltroEstado, SalidaHistorial } from '../../domain/models/historial';
import { estatusDeFiltro } from '../../domain/models/historial-reglas';
import { ObtenerHistorialUseCase } from '../../domain/use-cases/historial.use-cases';

export type EstadoHistorial = 'cargando' | 'listo' | 'vacio' | 'error';

/** Una "corriente" es la lista paginada de un estatus; "Pendientes" combina dos. */
interface Corriente {
  estatus: EstatusFiltro | null;
  siguiente: number;
  hayMas: boolean;
}

function marca(e: ElementoHistorial): number {
  const t = e.fechaTransaccion === null ? NaN : new Date(e.fechaTransaccion).getTime();
  return Number.isNaN(t) ? 0 : t;
}

/**
 * Historial de pagos del cliente. Singleton en memoria: siempre se pide al backend al entrar y nada se
 * persiste. Se limpia al cerrar sesión (incluye 401) y al cambiar de filtro.
 */
@Injectable({ providedIn: 'root' })
export class HistorialStore {
  private readonly obtener = inject(ObtenerHistorialUseCase);
  private readonly sesion = inject(SessionStore);

  private readonly _estado = signal<EstadoHistorial>('cargando');
  private readonly _elementos = signal<ElementoHistorial[]>([]);
  private readonly _filtro = signal<FiltroEstado>('todos');
  private readonly _hayMas = signal(false);
  private readonly _cargandoMas = signal(false);
  private readonly _errorMas = signal(false);

  private corrientes: Corriente[] = [];
  /** Se incrementa en cada reinicio para ignorar respuestas atrasadas. */
  private generacion = 0;

  readonly estado = this._estado.asReadonly();
  readonly elementos = this._elementos.asReadonly();
  readonly filtro = this._filtro.asReadonly();
  readonly hayMas = this._hayMas.asReadonly();
  readonly cargandoMas = this._cargandoMas.asReadonly();
  readonly errorMas = this._errorMas.asReadonly();

  constructor() {
    effect(() => {
      if (!this.sesion.haySesion()) this.limpiar();
    });
  }

  /** Pide la primera página del filtro actual (al entrar y al pulsar "Actualizar" o "Reintentar"). */
  async cargar(): Promise<void> {
    await this.iniciarCarga();
  }

  async seleccionarFiltro(filtro: FiltroEstado): Promise<void> {
    if (filtro === this._filtro() && this._estado() !== 'error') return;
    this._filtro.set(filtro);
    await this.iniciarCarga();
  }

  async verMas(): Promise<void> {
    if (this._cargandoMas() || !this._hayMas() || this._estado() !== 'listo') return;
    const generacion = this.generacion;
    this._cargandoMas.set(true);
    this._errorMas.set(false);
    const pendientes = this.corrientes.filter((c) => c.hayMas);
    const salidas = await Promise.all(pendientes.map((c) => this.obtener.ejecutar(c.estatus, c.siguiente)));
    if (generacion !== this.generacion) return;
    this._cargandoMas.set(false);
    if (!salidas.every((s) => s.tipo === 'pagina')) {
      this._errorMas.set(true);
      return;
    }
    this.aplicar(pendientes, salidas as Extract<SalidaHistorial, { tipo: 'pagina' }>[], true);
  }

  /** Vuelve al estado inicial (cierre de sesión o 401). */
  limpiar(): void {
    this.generacion++;
    this.corrientes = [];
    this._elementos.set([]);
    this._filtro.set('todos');
    this._hayMas.set(false);
    this._cargandoMas.set(false);
    this._errorMas.set(false);
    this._estado.set('cargando');
  }

  private async iniciarCarga(): Promise<void> {
    const generacion = ++this.generacion;
    this.corrientes = estatusDeFiltro(this._filtro()).map((estatus) => ({ estatus, siguiente: 0, hayMas: true }));
    this._elementos.set([]);
    this._hayMas.set(false);
    this._cargandoMas.set(false);
    this._errorMas.set(false);
    this._estado.set('cargando');
    const corrientes = this.corrientes;
    const salidas = await Promise.all(corrientes.map((c) => this.obtener.ejecutar(c.estatus, 0)));
    if (generacion !== this.generacion) return;
    if (!salidas.every((s) => s.tipo === 'pagina')) {
      this._estado.set('error');
      return;
    }
    this.aplicar(corrientes, salidas as Extract<SalidaHistorial, { tipo: 'pagina' }>[], false);
  }

  private aplicar(
    corrientes: Corriente[],
    salidas: Extract<SalidaHistorial, { tipo: 'pagina' }>[],
    acumular: boolean,
  ): void {
    const previos = acumular ? this._elementos() : [];
    const vistos = new Set(previos.map((e) => e.upc));
    const nuevos: ElementoHistorial[] = [];
    corrientes.forEach((c, i) => {
      const { pagina } = salidas[i];
      c.siguiente = pagina.pagina + 1;
      c.hayMas = pagina.hayMas;
      for (const e of pagina.elementos) {
        if (e.upc !== null && vistos.has(e.upc)) continue;
        if (e.upc !== null) vistos.add(e.upc);
        nuevos.push(e);
      }
    });
    const todos = [...previos, ...nuevos];
    if (this.corrientes.length > 1) todos.sort((a, b) => marca(b) - marca(a));
    this._elementos.set(todos);
    this._hayMas.set(this.corrientes.some((c) => c.hayMas));
    this._estado.set(todos.length === 0 ? 'vacio' : 'listo');
  }
}
