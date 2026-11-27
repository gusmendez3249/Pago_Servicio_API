import { Entrada } from '../../../../shared/entrada/entrada.directive';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Icono } from '../../../../shared/ui/icono/icono';
import { CategoriaConteo } from '../store/catalogo.store';
import { VisualCategoria, visualDeCategoria } from './categoria-visual';

/**
 * Filtros del catálogo. `seccion` decide qué parte se pinta: el buscador o los accesos circulares
 * por categoría; ambos viven en la franja negra.
 */
@Component({
  selector: 'app-catalogo-filtros',
  imports: [Icono, Entrada],
  templateUrl: './catalogo-filtros.html',
  styleUrl: './catalogo-filtros.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogoFiltros {
  readonly seccion = input<'busqueda' | 'categorias'>('categorias');
  readonly categorias = input<CategoriaConteo[]>([]);
  readonly total = input(0);
  readonly seleccionada = input<string | null>(null);
  readonly busqueda = input('');

  readonly categoriaCambia = output<string | null>();
  readonly busquedaCambia = output<string>();

  protected alEscribir(evento: Event): void {
    this.busquedaCambia.emit((evento.target as HTMLInputElement).value);
  }

  protected visual(nombre: string): VisualCategoria {
    return visualDeCategoria(nombre);
  }
}
