import { aProducto } from './producto-dto';

describe('aProducto', () => {
  it('tolera todos los campos nulos', () => {
    const p = aProducto({
      servicio: null, producto: null, idServicio: null, idProducto: null, idCatTipoServicio: null,
      tipoFront: null, hasDigitoVerificador: null, precio: null, showAyuda: null, tipoReferencia: null,
      legend: null, logoUrl: null, ayudaUrl: null, categoria: null,
    });
    expect(p).toEqual({
      servicio: '', producto: '', idServicio: null, idProducto: null, idCatTipoServicio: null,
      tipoFront: null, hasDigitoVerificador: false, precio: null, showAyuda: false, tipoReferencia: null,
      legend: null, logoUrl: null, ayudaUrl: null, categoria: null,
    });
  });

  it('tolera campos ausentes (backend sin los campos nuevos)', () => {
    const p = aProducto({ servicio: 'Luz' });
    expect(p.logoUrl).toBeNull();
    expect(p.ayudaUrl).toBeNull();
    expect(p.categoria).toBeNull();
  });

  it('conserva los valores presentes, con tipoFront entero y campos nuevos', () => {
    const p = aProducto({
      servicio: 'Luz', producto: 'CFE', idServicio: 56, idProducto: 185, precio: 10.5,
      hasDigitoVerificador: true, tipoFront: 2, logoUrl: 'https://x/y.png', ayudaUrl: ' https://x/a.png ',
      categoria: 'Pago de servicios',
    });
    expect(p).toMatchObject({
      servicio: 'Luz', producto: 'CFE', idServicio: 56, idProducto: 185, precio: 10.5,
      hasDigitoVerificador: true, tipoFront: 2, logoUrl: 'https://x/y.png', ayudaUrl: 'https://x/a.png',
      categoria: 'Pago de servicios',
    });
  });

  it('trata textos vacíos de los campos nuevos como nulos', () => {
    const p = aProducto({ logoUrl: '  ', ayudaUrl: '', categoria: ' ' });
    expect(p).toMatchObject({ logoUrl: null, ayudaUrl: null, categoria: null });
  });
});
