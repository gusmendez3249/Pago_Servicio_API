import { Failure } from '../../../../core/http/result';
import { mensajeAdmin } from './admin-page';

const falla = (httpStatus: number, tipo: Failure['tipo'], detalle?: unknown): Failure =>
  ({ ok: false, tipo, httpStatus, detalle }) as Failure;

describe('mensajeAdmin', () => {
  it('explica el tope diario de la cuenta y del ejecutivo sin texto del servidor', () => {
    expect(mensajeAdmin(falla(422, 'desconocido', { motivo: 'TOPE_DIARIO_CUENTA' }))).toContain('tope de recargas de hoy');
    expect(mensajeAdmin(falla(422, 'desconocido', { motivo: 'TOPE_DIARIO_EJECUTIVO' }))).toContain('tu tope');
  });

  it('cuenta inexistente y recarga en curso', () => {
    expect(mensajeAdmin(falla(404, 'no-encontrado'))).toContain('No existe una cuenta');
    expect(mensajeAdmin(falla(409, 'desconocido'))).toContain('ya se estaba procesando');
  });
});
