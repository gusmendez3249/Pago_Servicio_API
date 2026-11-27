import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { CabeceraFlujo } from '../../../../shared/ui/cabecera-flujo/cabecera-flujo';
import { Pasos } from '../../../../shared/ui/pasos/pasos';
import { admitePagoDirecto, admiteReferencia, categoriaDe, formatoMoneda, importeDe } from '../../domain/models/producto-reglas';
import { Icono } from '../../../../shared/ui/icono/icono';
import { LogoServicio } from '../components/logo-servicio';
import { CatalogoStore } from '../store/catalogo.store';

@Component({
  selector: 'app-producto-detalle-page',
  imports: [CabeceraFlujo, Icono, LogoServicio, Pasos, RouterLink],
  templateUrl: './producto-detalle-page.html',
  styleUrl: './producto-detalle-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductoDetallePage {
  protected readonly store = inject(CatalogoStore);
  private readonly ruta = inject(ActivatedRoute);

  private readonly ids = toSignal(
    this.ruta.paramMap.pipe(map((p) => ({ idServicio: Number(p.get('idServicio')), idProducto: Number(p.get('idProducto')) }))),
    { requireSync: true },
  );

  protected readonly producto = computed(() => {
    const { idServicio, idProducto } = this.ids();
    return this.store.buscarProducto(idServicio, idProducto);
  });
  protected readonly categoria = computed(() => {
    const p = this.producto();
    return p ? categoriaDe(p) : '';
  });
  protected readonly admiteReferencia = computed(() => {
    const p = this.producto();
    return p !== undefined && admiteReferencia(p);
  });
  protected readonly pagoDirecto = computed(() => {
    const p = this.producto();
    return p !== undefined && admitePagoDirecto(p);
  });
  protected readonly importe = computed(() => {
    const p = this.producto();
    const i = p ? importeDe(p) : null;
    return i === null ? null : { etiqueta: i.etiqueta, texto: formatoMoneda(i.monto) };
  });

  private readonly ayudaFallida = signal<string | null>(null);
  private readonly ayudaCargada = signal<string | null>(null);
  protected readonly ayudaEstado = computed<'cargando' | 'listo' | 'fallo'>(() => {
    const url = this.producto()?.ayudaUrl;
    if (!url || this.ayudaFallida() === url) return 'fallo';
    return this.ayudaCargada() === url ? 'listo' : 'cargando';
  });

  constructor() {
    void this.store.cargar();
  }

  protected ayudaCargo(): void {
    this.ayudaCargada.set(this.producto()?.ayudaUrl ?? null);
  }

  protected ayudaFallo(): void {
    this.ayudaFallida.set(this.producto()?.ayudaUrl ?? null);
  }
}
