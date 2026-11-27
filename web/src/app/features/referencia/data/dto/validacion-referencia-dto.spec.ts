import { aValidacion } from './validacion-referencia-dto';

describe('aValidacion', () => {
  const base = {
    estado: 'VALIDA', motivo: null, situacion: 'ADEUDO', saldo: 350, idValidacion: 'u', vigenciaSegundos: 300,
    puedePagar: true, idSer: 1, idProd: 2, referencia: 'A', servicio: 'S', producto: 'P',
  };

  it('mapea una respuesta válida', () => {
    expect(aValidacion(base)).toMatchObject({ estado: 'VALIDA', situacion: 'ADEUDO', saldo: 350, puedePagar: true });
  });

  it('mapea verificacion y montoAPagar; null se trata como EN_LINEA', () => {
    expect(aValidacion({ ...base, verificacion: 'SOLO_FORMATO', montoAPagar: null })).toMatchObject({
      verificacion: 'SOLO_FORMATO', montoAPagar: null,
    });
    expect(aValidacion({ ...base, verificacion: null, montoAPagar: 350 })).toMatchObject({
      verificacion: 'EN_LINEA', montoAPagar: 350,
    });
    expect(aValidacion(base)).toMatchObject({ verificacion: 'EN_LINEA', montoAPagar: null });
  });

  it('estado desconocido o ausente no es válido', () => {
    expect(aValidacion({ ...base, estado: 'OTRO' })).toBeNull();
    expect(aValidacion(null)).toBeNull();
  });

  it('enumerados desconocidos quedan en null', () => {
    expect(aValidacion({ ...base, situacion: 'X', motivo: 'Y' })).toMatchObject({ situacion: null, motivo: null });
  });
});
