import { CuentaCliente } from './pago';
import { calcularResumen, esPinValido, motivoRechazoDe, soloDigitos, validarImporte, validarTelefono } from './pago-reglas';
import { textoErrorImporte, textoErrorTelefono, textoPinIncorrecto } from './mensajes-pago';

const cuenta: CuentaCliente = { numCuenta: '695429041188', numCuentaEnmascarada: '********1188', saldo: 1000 };

describe('reglas de pago', () => {
  it('teléfono: 10 dígitos y debe coincidir', () => {
    expect(validarTelefono('555123456', '555123456')).toBe('formato');
    expect(validarTelefono('55512345678', '55512345678')).toBe('formato');
    expect(validarTelefono('5551234567', '5551234568')).toBe('noCoinciden');
    expect(validarTelefono('5551234567', '5551234567')).toBeNull();
    expect(soloDigitos('55-51a23')).toBe('555123');
  });

  it('importe: límites 1.00–10,000.00 y máximo 2 decimales', () => {
    expect(validarImporte('')).toEqual({ ok: false, error: 'vacio' });
    expect(validarImporte('0.99')).toEqual({ ok: false, error: 'rango' });
    expect(validarImporte('10000.01')).toEqual({ ok: false, error: 'rango' });
    expect(validarImporte('12.345')).toEqual({ ok: false, error: 'rango' });
    expect(validarImporte('abc')).toEqual({ ok: false, error: 'rango' });
    expect(validarImporte('1')).toEqual({ ok: true, monto: 1 });
    expect(validarImporte('1.00')).toEqual({ ok: true, monto: 1 });
    expect(validarImporte('10000.00')).toEqual({ ok: true, monto: 10000 });
    expect(validarImporte('350.5')).toEqual({ ok: true, monto: 350.5 });
  });

  it('PIN: exactamente 4 dígitos', () => {
    expect(esPinValido('1234')).toBe(true);
    expect(esPinValido('123')).toBe(false);
    expect(esPinValido('12a4')).toBe(false);
  });

  it('resumen: saldo después y saldo insuficiente (centavos exactos)', () => {
    expect(calcularResumen(350.1, cuenta)).toEqual({
      total: 350.1, cuentaEnmascarada: '********1188', saldo: 1000, saldoDespues: 649.9, saldoInsuficiente: false,
    });
    expect(calcularResumen(1000, cuenta).saldoInsuficiente).toBe(false);
    expect(calcularResumen(1000.01, cuenta).saldoInsuficiente).toBe(true);
  });

  it('motivo desconocido cae en el rechazo genérico', () => {
    expect(motivoRechazoDe('REFERENCIA_INVALIDA')).toBe('REFERENCIA_INVALIDA');
    expect(motivoRechazoDe('ERROR_GESTOPAGO')).toBe('ERROR_GESTOPAGO');
    expect(motivoRechazoDe('DESCONOCIDO')).toBe('RECHAZADO_PROVEEDOR');
    expect(motivoRechazoDe(null)).toBe('RECHAZADO_PROVEEDOR');
  });

  it('textos exactos de la especificación', () => {
    expect(textoErrorTelefono('formato')).toBe('Ingresa un número de 10 dígitos.');
    expect(textoErrorTelefono('noCoinciden')).toBe('Los números no coinciden.');
    expect(textoErrorImporte('vacio')).toBe('Ingresa el importe de tu recibo.');
    expect(textoErrorImporte('rango')).toBe('El importe debe estar entre $1.00 y $10,000.00.');
    expect(textoPinIncorrecto(2)).toBe('PIN incorrecto. Te quedan 2 intentos.');
    expect(textoPinIncorrecto(1)).toBe('PIN incorrecto. Te queda 1 intento.');
    expect(textoPinIncorrecto(null)).toBe('PIN incorrecto. Inténtalo de nuevo.');
  });
});
