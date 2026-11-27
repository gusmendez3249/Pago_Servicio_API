import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Producto } from '../../domain/models/producto';
import { categoriaDe, formatoMoneda, importeDe } from '../../domain/models/producto-reglas';
import { Icono } from '../../../../shared/ui/icono/icono';
import { LogoServicio } from './logo-servicio';

@Component({
  selector: 'app-servicio-card',
  imports: [Icono, NgTemplateOutlet, RouterLink, LogoServicio],
  templateUrl: './servicio-card.html',
  styleUrl: './servicio-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicioCard {
  readonly producto = input.required<Producto>();

  protected readonly categoria = computed(() => categoriaDe(this.producto()));
  protected readonly importe = computed(() => {
    const i = importeDe(this.producto());
    return i === null ? null : { etiqueta: i.etiqueta, texto: formatoMoneda(i.monto) };
  });
  protected readonly enlace = computed(() => {
    const { idServicio, idProducto } = this.producto();
    return idServicio === null || idProducto === null ? null : ['/catalogo', idServicio, idProducto];
  });
}
