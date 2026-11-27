import { Injectable, inject } from '@angular/core';
import { ConsultaEstado, SalidaCuentas, SalidaEstado, SalidaPago, SolicitudPago } from '../models/pago';
import { PagoRepository } from '../repository/pago-repository';

@Injectable({ providedIn: 'root' })
export class ObtenerCuentasUseCase {
  private readonly repositorio = inject(PagoRepository);
  ejecutar(): Promise<SalidaCuentas> {
    return this.repositorio.cuentasMias();
  }
}

@Injectable({ providedIn: 'root' })
export class PagarServicioUseCase {
  private readonly repositorio = inject(PagoRepository);
  ejecutar(solicitud: SolicitudPago, idempotencyKey: string): Promise<SalidaPago> {
    return this.repositorio.pagar(solicitud, idempotencyKey);
  }
}

@Injectable({ providedIn: 'root' })
export class ConsultarEstadoPagoUseCase {
  private readonly repositorio = inject(PagoRepository);
  ejecutar(consulta: ConsultaEstado): Promise<SalidaEstado> {
    return this.repositorio.consultarEstado(consulta);
  }
}
