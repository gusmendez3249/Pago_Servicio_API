import { TITULO_ERROR_GESTOPAGO, TITULO_RECHAZADO, textoRechazo, tituloDeRechazo } from './mensajes-pago';

describe('mensajes de rechazo', () => {
  it('un error propio de Gesto Pago se nombra como tal', () => {
    expect(tituloDeRechazo('ERROR_GESTOPAGO')).toBe(TITULO_ERROR_GESTOPAGO);
    expect(textoRechazo('ERROR_GESTOPAGO').cuerpo).toContain('Gesto Pago');
  });
  it('los demás rechazos conservan el título genérico', () => {
    expect(tituloDeRechazo('RECHAZADO_PROVEEDOR')).toBe(TITULO_RECHAZADO);
    expect(textoRechazo('RECHAZADO_PROVEEDOR').cuerpo).not.toContain('Gesto Pago');
  });
});
