import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { SessionStore } from '../../../../core/session/session-store';
import { RouterLink } from '@angular/router';
import { Banner } from '../../../../shared/ui/banner/banner';
import { Cabecera } from '../../../../shared/ui/cabecera/cabecera';
import { Icono } from '../../../../shared/ui/icono/icono';
import { CatalogoFiltros } from '../components/catalogo-filtros';
import { ServicioCard } from '../components/servicio-card';
import { CatalogoStore } from '../store/catalogo.store';
import { claveProducto } from '../../domain/models/producto-reglas';

@Component({
  selector: 'app-catalogo-page',
  imports: [Banner, Cabecera, CatalogoFiltros, Icono, RouterLink, ServicioCard],
  templateUrl: './catalogo-page.html',
  styleUrl: './catalogo-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogoPage {
  protected readonly store = inject(CatalogoStore);
  protected readonly esqueletos = [1, 2, 3, 4, 5, 6, 7, 8];
  protected readonly clave = claveProducto;

  private readonly sesion = inject(SessionStore);
  /** "Hola, Ana" con el primer nombre del cliente; solo presentación. */
  protected readonly saludo = computed(() => {
    const nombre = this.sesion.nombreCliente()?.trim().split(/\s+/)[0];
    if (!nombre) return null;
    return `Hola, ${nombre.charAt(0).toLocaleUpperCase('es-MX')}${nombre.slice(1).toLocaleLowerCase('es-MX')}`;
  });

  constructor() {
    void this.store.cargar();
  }
}
