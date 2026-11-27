import { Failure, Result } from '../../../../core/http/result';
import { PagoServicioResponseDto, aPago, aCuentas } from '../dto/pago-dto';
import { interpretarCuentas, interpretarEstado, interpretarPago } from './interpretar-respuesta';

const dto = (p: Partial<PagoServicioResponseDto> = {}): PagoServicioResponseDto => ({
  numCuenta: '********1188', upc: 'K3J9X0A7ZQ', estatus: 'CONFIRMADA', motivo: null, monto: 100, saldoRestante: 900,
  folio: 'F1', mensajeRespuesta: 'TEXTO TECNICO', codigoRespuesta: '01', ...p,
});
const ok = <T>(data: T, httpStatus = 200): Result<T> => ({ ok: true, data, mensaje: 'x', httpStatus });
const falla = (httpStatus: number, detalle?: unknown, tipo: Failure['tipo'] = 'desconocido'): Failure => ({
  ok: false, tipo, httpStatus, codigo: httpStatus, mensaje: 'texto del servidor', detalle,
});

describe('interpretación de respuestas de pago', () => {
  it('200 CONFIRMADA => exitoso con folio; no copia textos del servidor', () => {
    const s = interpretarPago(ok(dto()));
    expect(s.tipo).toBe('exitoso');
    expect(JSON.stringify(s)).not.toContain('TEXTO TECNICO');
  });

  it('202 => pendiente; estatus desconocido nunca es éxito', () => {
    expect(interpretarPago(ok(dto({ estatus: 'INDETERMINADA', folio: null }), 202)).tipo).toBe('pendiente');
    expect(interpretarPago(ok(dto({ estatus: 'PENDIENTE' }), 202)).tipo).toBe('pendiente');
    expect(interpretarPago(ok(dto({ estatus: 'NUEVO_ESTATUS' }))).tipo).toBe('pendiente');
    expect(aPago(dto({ estatus: 'NUEVO_ESTATUS' }))?.estatus).toBe('DESCONOCIDO');
  });

  it('200 con estatus RECHAZADA no se muestra como éxito', () => {
    expect(interpretarPago(ok(dto({ estatus: 'RECHAZADA', motivo: 'RECHAZADO_PROVEEDOR' }))).tipo).toBe('rechazado');
  });

  it('422 => rechazado con el motivo del pago', () => {
    const s = interpretarPago(falla(422, dto({ estatus: 'RECHAZADA', motivo: 'REFERENCIA_INVALIDA' })));
    expect(s).toMatchObject({ tipo: 'rechazado', motivo: 'REFERENCIA_INVALIDA' });
    expect(interpretarPago(falla(422, dto({ estatus: 'RECHAZADA', motivo: 'ALGO_NUEVO' })))).toMatchObject({ motivo: 'RECHAZADO_PROVEEDOR' });
  });

  it('403 PIN_INCORRECTO conserva intentos; otros 403 son genéricos', () => {
    expect(interpretarPago(falla(403, { motivo: 'PIN_INCORRECTO', intentosRestantes: 2 }))).toEqual({ tipo: 'pinIncorrecto', intentosRestantes: 2 });
    expect(interpretarPago(falla(403, { motivo: 'PIN_INCORRECTO' }))).toEqual({ tipo: 'pinIncorrecto', intentosRestantes: null });
    expect(interpretarPago(falla(403, { motivo: 'SIN_PIN' })).tipo).toBe('generico');
  });

  it('423, 428, 400 saldo, 5xx, 401, red y otros', () => {
    expect(interpretarPago(falla(423, { motivo: 'PIN_BLOQUEADO', minutosBloqueo: 15 })).tipo).toBe('pinBloqueado');
    expect(interpretarPago(falla(428, { motivo: 'VALIDACION_REQUERIDA' })).tipo).toBe('validacionRequerida');
    expect(interpretarPago(falla(400, { motivo: 'SALDO_INSUFICIENTE_CUENTA' })).tipo).toBe('saldoInsuficiente');
    expect(interpretarPago(falla(400, { motivo: 'MONTO_NO_PERMITIDO' })).tipo).toBe('generico');
    for (const c of [500, 502, 503]) {
      expect(interpretarPago(falla(c, { motivo: 'PROVEEDOR_NO_DISPONIBLE' }, 'servidor'))).toMatchObject({ tipo: 'rechazado', motivo: 'PROVEEDOR_NO_DISPONIBLE' });
    }
    expect(interpretarPago(falla(401)).tipo).toBe('noAutenticado');
    expect(interpretarPago(falla(0, undefined, 'red')).tipo).toBe('sinRed');
    for (const c of [404, 409]) expect(interpretarPago(falla(c, { motivo: 'LLAVE_EN_USO' })).tipo).toBe('generico');
  });

  it('5xx sin motivo PROVEEDOR_NO_DISPONIBLE, 504, 2xx con codigo != 0 y 2xx sin data => pendiente (nunca "sin cargo")', () => {
    for (const c of [500, 502, 503]) {
      expect(interpretarPago(falla(c, undefined, 'servidor'))).toEqual({ tipo: 'pendiente', pago: null });
      expect(interpretarPago(falla(c, { motivo: 'OTRO' }, 'servidor'))).toEqual({ tipo: 'pendiente', pago: null });
    }
    expect(interpretarPago(falla(504, { motivo: 'PROVEEDOR_NO_DISPONIBLE' }, 'servidor'))).toEqual({ tipo: 'pendiente', pago: null });
    expect(interpretarPago(falla(200))).toEqual({ tipo: 'pendiente', pago: null });
    expect(interpretarPago(ok(null as never, 200))).toEqual({ tipo: 'pendiente', pago: null });
  });

  it('estado: pago, 404, 401, red', () => {
    expect(interpretarEstado(ok(dto())).tipo).toBe('pago');
    expect(interpretarEstado(falla(404, { motivo: 'OPERACION_NO_ENCONTRADA' })).tipo).toBe('noEncontrado');
    expect(interpretarEstado(falla(401)).tipo).toBe('noAutenticado');
    expect(interpretarEstado(falla(0, undefined, 'red')).tipo).toBe('sinRed');
  });

  it('cuentas: tolera nulos, descarta inactivas y enmascara si falta', () => {
    expect(aCuentas([{ numCuenta: '695429041188', saldo: 10, estatus: 'ACTIVA' }, { numCuenta: null, saldo: 1 }, { numCuenta: '1', saldo: 1, estatus: 'BLOQUEADA' }])).toEqual([
      { numCuenta: '695429041188', numCuentaEnmascarada: '********1188', saldo: 10 },
    ]);
    expect(interpretarCuentas(ok(null as never))).toEqual({ tipo: 'cuentas', cuentas: [] });
    expect(interpretarCuentas(falla(403, { motivo: 'NO_TITULAR' })).tipo).toBe('generico');
  });

  it('el pago tolera campos nulos', () => {
    const p = aPago({});
    expect(p).toMatchObject({ estatus: 'DESCONOCIDO', folio: null, monto: null, pinServicio: null, leyenda: null });
  });
});
