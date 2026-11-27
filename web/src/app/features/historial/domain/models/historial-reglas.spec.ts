import { estadoVisualDe, estatusDeFiltro, etiquetaEstado, etiquetaIdentificador, formatoFechaHora, identificadorDe } from './historial-reglas';

describe('historial-reglas', () => {
  it('mapea el estatus al estado visual', () => {
    expect(estadoVisualDe('CONFIRMADA')).toBe('exitoso');
    expect(estadoVisualDe('RECHAZADA')).toBe('rechazado');
    expect(estadoVisualDe('INDETERMINADA')).toBe('pendiente');
    expect(estadoVisualDe('PENDIENTE')).toBe('pendiente');
    expect(estadoVisualDe('DESCONOCIDO')).toBe('pendiente');
  });

  it('usa las etiquetas de la especificación', () => {
    expect(etiquetaEstado('exitoso')).toBe('Exitoso');
    expect(etiquetaEstado('rechazado')).toBe('Rechazado');
    expect(etiquetaEstado('pendiente')).toBe('Pendiente');
  });

  it('formatea la fecha en es-MX con hora de 24 h', () => {
    expect(formatoFechaHora(new Date(2026, 10, 13, 19, 39).toISOString())).toBe('13 nov 2026, 19:39');
    expect(formatoFechaHora('2026-01-05T08:05:00')).toBe('5 ene 2026, 08:05');
  });

  it('tolera fecha ausente o ilegible', () => {
    expect(formatoFechaHora(null)).toBeNull();
    expect(formatoFechaHora('ayer')).toBe('ayer');
  });

  it('etiqueta Folio si hay folio y Número de operación si no', () => {
    expect(etiquetaIdentificador({ folio: 'F1' })).toBe('Folio');
    expect(etiquetaIdentificador({ folio: null })).toBe('Número de operación');
    expect(identificadorDe({ folio: null, identificadorOperacion: 'OP9', idTx: null, upc: 'U' })).toBe('OP9');
  });

  it('Pendientes pide PENDIENTE e INDETERMINADA por separado', () => {
    expect(estatusDeFiltro('todos')).toEqual([null]);
    expect(estatusDeFiltro('exitosos')).toEqual(['CONFIRMADA']);
    expect(estatusDeFiltro('rechazados')).toEqual(['RECHAZADA']);
    expect(estatusDeFiltro('pendientes')).toEqual(['PENDIENTE', 'INDETERMINADA']);
  });
});
