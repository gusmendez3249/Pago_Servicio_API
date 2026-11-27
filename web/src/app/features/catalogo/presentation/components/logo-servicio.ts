import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { inicialesDe } from '../../domain/models/producto-reglas';

/** Logotipo con placeholder de tamaño fijo mientras carga y avatar de iniciales si falta o falla. */
@Component({
  selector: 'app-logo-servicio',
  templateUrl: './logo-servicio.html',
  styleUrl: './logo-servicio.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.logo--grande]': "tamano() === 'grande'" },
})
export class LogoServicio {
  readonly url = input<string | null>(null);
  readonly nombre = input<string>('');
  readonly tamano = input<'normal' | 'grande'>('normal');

  private readonly urlFallida = signal<string | null>(null);
  private readonly urlCargada = signal<string | null>(null);

  protected readonly iniciales = computed(() => inicialesDe(this.nombre()));
  protected readonly alt = computed(() => (this.nombre() ? `Logotipo de ${this.nombre()}` : 'Logotipo del servicio'));

  readonly estado = computed<'cargando' | 'listo' | 'respaldo'>(() => {
    const url = this.url();
    if (!url || this.urlFallida() === url) return 'respaldo';
    return this.urlCargada() === url ? 'listo' : 'cargando';
  });

  protected alCargar(): void {
    this.urlCargada.set(this.url());
  }

  protected alFallar(): void {
    this.urlFallida.set(this.url());
  }
}
