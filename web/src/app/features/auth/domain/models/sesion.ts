import { Rol } from '../../../../core/session/session-store';

export type { Rol };

export interface Credenciales {
  username: string;
  password: string;
}

export interface Sesion {
  username: string;
  nombreCliente: string;
  token: string;
  rol: Rol;
  expiraEnMinutos: number;
}
