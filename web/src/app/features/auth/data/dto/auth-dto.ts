import { Rol } from '../../../../core/session/session-store';
import { Credenciales, Sesion } from '../../domain/models/sesion';

export interface LoginRequestDto {
  bandera: 1;
  username: string;
  password: string;
}

export interface LoginResponseDto {
  username: string;
  nombreCliente: string;
  isLoggedIn: boolean;
  token: string;
  tipoToken: string;
  expiraEnMinutos: number;
  rol: Rol;
}

export function aLoginRequestDto(c: Credenciales): LoginRequestDto {
  return { bandera: 1, username: c.username, password: c.password };
}

export function aSesion(dto: LoginResponseDto): Sesion {
  return {
    username: dto.username,
    nombreCliente: dto.nombreCliente,
    token: dto.token,
    rol: dto.rol,
    expiraEnMinutos: dto.expiraEnMinutos,
  };
}
