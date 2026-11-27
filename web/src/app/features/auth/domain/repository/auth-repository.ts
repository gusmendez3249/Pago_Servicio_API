import { Result } from '../../../../core/http/result';
import { Credenciales, Sesion } from '../models/sesion';

/** Contrato del repositorio de autenticación (la implementación vive en data/). */
export abstract class AuthRepository {
  abstract login(credenciales: Credenciales): Promise<Result<Sesion>>;
  abstract logout(): Promise<Result<null>>;
}
