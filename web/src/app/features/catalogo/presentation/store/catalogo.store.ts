import { Injectable, computed, inject, signal } from '@angular/core';
import { mensajeGenerico } from '../../../../core/error/mensajes-error';
import { Producto } from '../../domain/models/producto';
import { categoriaDe, normalizarTexto } from '../../domain/models/producto-reglas';
import { ObtenerCatalogoUseCase } from '../../domain/use-cases/obtener-catalogo.use-case';

export type EstadoCatalogo = 'cargando' | 'listo' | 'vacio' | 'error';

export interface CategoriaConteo {
  nombre: string;
  conteo: number;
}

@Injectable({ providedIn: 'root' })
export class CatalogoStore {
  private readonly obtenerCatalogo = inject(ObtenerCatalogoUseCase);

  private readonly _estado = signal<EstadoCatalogo>('cargando');
  private readonly _productos = signal<Producto[]>([]);
  private readonly _mensajeError = signal<string | null>(null);
  private readonly _categoria = signal<string | null>(null);
  private readonly _busqueda = signal('');
  private yaCargado = false;
  private enCurso = false;

  readonly estado = this._estado.asReadonly();
  readonly productos = this._productos.asReadonly();
  readonly mensajeError = this._mensajeError.asReadonly();
  /** null = "Todas". */
  readonly categoriaSeleccionada = this._categoria.asReadonly();
  readonly busqueda = this._busqueda.asReadonly();

  readonly categorias = computed<CategoriaConteo[]>(() => {
    const conteos = new Map<string, number>();
    for (const p of this._productos()) {
      const nombre = categoriaDe(p);
      conteos.set(nombre, (conteos.get(nombre) ?? 0) + 1);
    }
    return [...conteos]
      .map(([nombre, conteo]) => ({ nombre, conteo }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  readonly filtrados = computed(() => {
    const categoria = this._categoria();
    const texto = normalizarTexto(this._busqueda());
    return this._productos().filter(
      (p) =>
        (categoria === null || categoriaDe(p) === categoria) &&
        (texto === '' || normalizarTexto(p.servicio).includes(texto) || normalizarTexto(p.producto).includes(texto)),
    );
  });

  readonly hayFiltros = computed(() => this._categoria() !== null || this._busqueda().trim() !== '');

  /** Carga el catálogo si aún no se ha cargado con éxito (reutiliza lo ya cargado). */
  async cargar(): Promise<void> {
    if (this.enCurso || (this.yaCargado && this._estado() !== 'error')) return;
    await this.recargar();
  }

  async recargar(): Promise<void> {
    if (this.enCurso) return;
    this.enCurso = true;
    this._estado.set('cargando');
    this._mensajeError.set(null);
    try {
      const resultado = await this.obtenerCatalogo.ejecutar();
      if (resultado.ok) {
        this._productos.set(resultado.data);
        this.yaCargado = true;
        this._estado.set(resultado.data.length === 0 ? 'vacio' : 'listo');
        const sel = this._categoria();
        if (sel !== null && !this.categorias().some((c) => c.nombre === sel)) this._categoria.set(null);
      } else {
        this._mensajeError.set(mensajeGenerico(resultado));
        this._estado.set('error');
      }
    } finally {
      this.enCurso = false;
    }
  }

  seleccionarCategoria(nombre: string | null): void {
    this._categoria.set(nombre);
  }

  buscar(texto: string): void {
    this._busqueda.set(texto);
  }

  limpiarFiltros(): void {
    this._categoria.set(null);
    this._busqueda.set('');
  }

  buscarProducto(idServicio: number, idProducto: number): Producto | undefined {
    return this._productos().find((p) => p.idServicio === idServicio && p.idProducto === idProducto);
  }
}
