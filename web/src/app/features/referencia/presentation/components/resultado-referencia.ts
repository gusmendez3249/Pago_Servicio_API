import { ChangeDetectionStrategy, Component, ElementRef, input, viewChild } from '@angular/core';
import { Icono, NombreIcono } from '../../../../shared/ui/icono/icono';
import { ResultadoVisible } from '../store/validacion-referencia.store';

/** Mensaje de resultado: tono visual distinto, icono y foco programático para lectores de pantalla. */
@Component({
  selector: 'app-resultado-referencia',
  imports: [Icono],
  templateUrl: './resultado-referencia.html',
  styleUrl: './resultado-referencia.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultadoReferencia {
  readonly resultado = input.required<ResultadoVisible>();
  private readonly cuerpo = viewChild<ElementRef<HTMLElement>>('cuerpo');

  enfocar(): void {
    this.cuerpo()?.nativeElement.focus();
  }

  protected icono(): NombreIcono {
    switch (this.resultado().tono) {
      case 'ok':
        return 'check';
      case 'error':
      case 'aviso':
        return 'alerta';
      case 'info':
        return 'info';
      default:
        return 'reloj';
    }
  }
}
