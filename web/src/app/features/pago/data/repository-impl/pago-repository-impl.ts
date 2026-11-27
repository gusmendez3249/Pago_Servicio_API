import { Injectable, inject } from '@angular/core';
import { ConsultaEstado, SalidaCuentas, SalidaEstado, SalidaPago, SolicitudPago } from '../../domain/models/pago';
import { PagoRepository } from '../../domain/repository/pago-repository';
import { PagoApi } from '../api/pago-api';
import { interpretarCuentas, interpretarEstado, interpretarPago } from '../mapeo/interpretar-respuesta';

@Injectable()
export class PagoRepositoryImpl extends PagoRepository {
  private readonly api = inject(PagoApi);

  async cuentasMias(): Promise<SalidaCuentas> {
    return interpretarCuentas(await this.api.cuentasMias());
  }

  async pagar(solicitud: SolicitudPago, idempotencyKey: string): Promise<SalidaPago> {
    return interpretarPago(await this.api.pagar({ ...solicitud }, idempotencyKey));
  }

  async consultarEstado(consulta: ConsultaEstado): Promise<SalidaEstado> {
    return interpretarEstado(await this.api.estado({ ...consulta }));
  }
}
