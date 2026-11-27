import { ChangeDetectionStrategy, Component, Injector, afterNextRender, computed, effect, inject, signal, untracked, viewChild, ElementRef } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { CabeceraFlujo } from '../../../../shared/ui/cabecera-flujo/cabecera-flujo';
import { Pasos } from '../../../../shared/ui/pasos/pasos';
import { Icono } from '../../../../shared/ui/icono/icono';
import { admiteReferencia } from '../../../catalogo/domain/models/producto-reglas';
import { LogoServicio } from '../../../catalogo/presentation/components/logo-servicio';
import { CatalogoStore } from '../../../catalogo/presentation/store/catalogo.store';
import { LONGITUD_MAXIMA_REFERENCIA } from '../../domain/models/formato-referencia';
import { ResultadoReferencia } from '../components/resultado-referencia';
import { ValidacionReferenciaStore } from '../store/validacion-referencia.store';

@Component({
  selector: 'app-referencia-page',
  imports: [CabeceraFlujo, Icono, LogoServicio, Pasos, ResultadoReferencia, RouterLink],
  templateUrl: './referencia-page.html',
  styleUrl: './referencia-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReferenciaPage {
  protected readonly store = inject(ValidacionReferenciaStore);
  protected readonly catalogo = inject(CatalogoStore);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  protected readonly maxlength = LONGITUD_MAXIMA_REFERENCIA;

  private readonly ids = toSignal(
    this.ruta.paramMap.pipe(
      map((p) => ({ idServicio: Number(p.get('idServicio')), idProducto: Number(p.get('idProducto')) })),
    ),
    { requireSync: true },
  );

  protected readonly producto = computed(() => {
    const { idServicio, idProducto } = this.ids();
    return this.catalogo.buscarProducto(idServicio, idProducto);
  });
  protected readonly soportado = computed(() => {
    const p = this.producto();
    return p !== undefined && admiteReferencia(p);
  });

  protected readonly ayudaVisible = signal(false);
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');
  private readonly resultadoCmp = viewChild(ResultadoReferencia);

  protected readonly mostrarResultado = computed(() => this.store.resultado() !== null);

  constructor() {
    void this.catalogo.cargar();

    effect(() => {
      const p = this.producto();
      if (p === undefined || !admiteReferencia(p) || p.idServicio === null || p.idProducto === null) return;
      const ref = {
        idSer: p.idServicio,
        idProd: p.idProducto,
        servicio: p.servicio,
        producto: p.producto,
        logoUrl: p.logoUrl,
        ayudaUrl: p.ayudaUrl,
      };
      untracked(() => this.store.iniciar(ref));
    });

    // Foco al resultado (o al campo si el formato es incorrecto) tras validar.
    effect(() => {
      const estado = this.store.estado();
      if (estado === 'capturando' || estado === 'validando') return;
      afterNextRender(
        () => {
          if (estado === 'formatoInvalido') this.campo()?.nativeElement.focus();
          else this.resultadoCmp()?.enfocar();
        },
        { injector: this.injector },
      );
    });
  }

  protected alEscribir(evento: Event): void {
    this.store.editar((evento.target as HTMLInputElement).value);
  }

  protected validar(evento: Event): void {
    evento.preventDefault();
    void this.store.validar();
  }

  protected continuar(): void {
    const datos = this.store.datosPago();
    if (datos === null) return;
    void this.router.navigate(['/catalogo', datos.idSer, datos.idProd, 'pago']);
  }

  protected alternarAyuda(): void {
    this.ayudaVisible.update((v) => !v);
  }
}
