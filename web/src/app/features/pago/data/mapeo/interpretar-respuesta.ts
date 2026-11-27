import { Failure, Result } from '../../../../core/http/result';
import { SalidaCuentas, SalidaEstado, SalidaPago } from '../../domain/models/pago';
import { motivoRechazoDe } from '../../domain/models/pago-reglas';
import { CuentaDto, PagoServicioResponseDto, aCuentas, aPago, leerDetalle } from '../dto/pago-dto';

/** Traduce la respuesta HTTP de un pago al resultado que entiende el dominio (tabla de transiciones). */
export function interpretarPago(r: Result<PagoServicioResponseDto>): SalidaPago {
  if (r.ok) {
    const pago = aPago(r.data);
    // 2xx sin datos del pago: no se sabe si se cobró; se consulta por llave.
    if (pago === null) return { tipo: 'pendiente', pago: null };
    // Solo CONFIRMADA es éxito, sin importar el código HTTP.
    if (pago.estatus === 'CONFIRMADA') return { tipo: 'exitoso', pago };
    if (pago.estatus === 'RECHAZADA') return { tipo: 'rechazado', motivo: motivoRechazoDe(pago.motivo), pago };
    return { tipo: 'pendiente', pago };
  }
  return interpretarFalloPago(r);
}

function interpretarFalloPago(f: Failure): SalidaPago {
  const { motivo, intentosRestantes } = leerDetalle(f.detalle);
  // 2xx con código distinto de 0: resultado incierto, se verifica por llave.
  if (f.httpStatus >= 200 && f.httpStatus < 300) return { tipo: 'pendiente', pago: null };
  switch (f.httpStatus) {
    case 401:
      return { tipo: 'noAutenticado' };
    case 422: {
      const pago = aPago(f.detalle);
      return { tipo: 'rechazado', motivo: motivoRechazoDe(pago?.motivo ?? motivo), pago };
    }
    case 403:
      return motivo === 'PIN_INCORRECTO' ? { tipo: 'pinIncorrecto', intentosRestantes } : { tipo: 'generico' };
    case 423:
      return { tipo: 'pinBloqueado' };
    case 428:
      return { tipo: 'validacionRequerida' };
    case 400:
      return motivo === 'SALDO_INSUFICIENTE_CUENTA' ? { tipo: 'saldoInsuficiente' } : { tipo: 'generico' };
    case 500:
    case 502:
    case 503:
      // Solo con motivo explícito se asegura que no hubo cargo; cualquier otro 5xx es incierto.
      return motivo === 'PROVEEDOR_NO_DISPONIBLE'
        ? { tipo: 'rechazado', motivo: 'PROVEEDOR_NO_DISPONIBLE', pago: null }
        : { tipo: 'pendiente', pago: null };
    case 504:
      return { tipo: 'pendiente', pago: null };
    case 0:
      return f.tipo === 'red' ? { tipo: 'sinRed' } : { tipo: 'generico' };
    default:
      return { tipo: 'generico' };
  }
}

export function interpretarEstado(r: Result<PagoServicioResponseDto>): SalidaEstado {
  if (r.ok) {
    const pago = aPago(r.data);
    return pago === null ? { tipo: 'generico' } : { tipo: 'pago', pago };
  }
  switch (r.httpStatus) {
    case 401:
      return { tipo: 'noAutenticado' };
    case 404:
      return { tipo: 'noEncontrado' };
    case 0:
      return r.tipo === 'red' ? { tipo: 'sinRed' } : { tipo: 'generico' };
    default:
      return { tipo: 'generico' };
  }
}

export function interpretarCuentas(r: Result<CuentaDto[]>): SalidaCuentas {
  if (r.ok) return { tipo: 'cuentas', cuentas: aCuentas(r.data) };
  return r.httpStatus === 401 ? { tipo: 'noAutenticado' } : { tipo: 'generico' };
}
