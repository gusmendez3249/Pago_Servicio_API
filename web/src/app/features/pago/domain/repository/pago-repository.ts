import { ConsultaEstado, SalidaCuentas, SalidaEstado, SalidaPago, SolicitudPago } from '../models/pago';

export abstract class PagoRepository {
  abstract cuentasMias(): Promise<SalidaCuentas>;
  abstract pagar(solicitud: SolicitudPago, idempotencyKey: string): Promise<SalidaPago>;
  abstract consultarEstado(consulta: ConsultaEstado): Promise<SalidaEstado>;
}
