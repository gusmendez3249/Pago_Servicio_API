import { Injectable, inject, signal } from '@angular/core';
import { ResultadoPago } from '../../../pago/domain/models/pago';
import { ObtenerDetallePagoUseCase } from '../../domain/use-cases/historial.use-cases';

export type EstadoDetalle = 'cargando' | 'listo' | 'noEncontrado' | 'error';

/** Detalle de una operación; se provee a nivel de página, así que vive y muere con la pantalla. */
@Injectable()
export class DetalleHistorialStore {
  private readonly obtener = inject(ObtenerDetallePagoUseCase);

  private readonly _estado = signal<EstadoDetalle>('cargando');
  private readonly _pago = signal<ResultadoPago | null>(null);
  private readonly _consultando = signal(false);
  private upc = '';
  private generacion = 0;

  readonly estado = this._estado.asReadonly();
  readonly pago = this._pago.asReadonly();
  /** Verdadero mientras se reconsulta una operación pendiente (el comprobante sigue visible). */
  readonly consultando = this._consultando.asReadonly();

  async cargar(upc: string): Promise<void> {
    const generacion = ++this.generacion;
    this.upc = upc;
    this._pago.set(null);
    this._consultando.set(false);
    this._estado.set('cargando');
    const salida = await this.obtener.ejecutar(upc);
    if (generacion !== this.generacion) return;
    switch (salida.tipo) {
      case 'pago':
        this._pago.set(salida.pago);
        this._estado.set('listo');
        return;
      case 'noEncontrado':
        this._estado.set('noEncontrado');
        return;
      default:
        this._estado.set('error');
    }
  }

  /** "Consultar de nuevo": si falla se conserva lo que ya se mostraba. */
  async consultarDeNuevo(): Promise<void> {
    if (this._consultando() || this._pago() === null) return;
    const generacion = this.generacion;
    this._consultando.set(true);
    const salida = await this.obtener.ejecutar(this.upc);
    if (generacion !== this.generacion) return;
    this._consultando.set(false);
    if (salida.tipo === 'pago') this._pago.set(salida.pago);
  }

  async reintentar(): Promise<void> {
    await this.cargar(this.upc);
  }
}
