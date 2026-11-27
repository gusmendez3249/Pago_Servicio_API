import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../../core/http/api-client';
import { Result } from '../../../core/http/result';

export interface Nacionalidad {
  id: number;
  nombre: string;
}

export interface SolicitudRegistro {
  bandera: 1;
  datosPersonales: {
    nombre: string;
    segundoNombre?: string;
    apellidoPaterno: string;
    apellidoMaterno: string;
    fechaNacimiento: string;
    curp: string;
    rfc: string;
    sexo: string;
    nacionalidad: string;
    estadoCivil: string;
  };
  datosContacto: { correo: string; telefonoMovil: number };
  domicilio: {
    calle: string;
    numeroExterior: string;
    numeroInterior?: string;
    colonia: string;
    municipio: string;
    estado: string;
    codigoPostal: string;
    pais: string;
  };
  informacionLaboral: { ocupacion: string; empresa: string; ingresoMensual: number };
  loginCredenciales: { username: string; password: string; pin: string };
}

export interface CuentaCreada {
  nombreCompleto: string;
  username: string;
  numeroCuenta: string;
  saldoInicial: number;
}

/** Registro público de clientes (bandera 1) y catálogo de nacionalidades. */
@Injectable({ providedIn: 'root' })
export class RegistroApi {
  private readonly api = inject(ApiClient);

  nacionalidades(): Promise<Result<Nacionalidad[]>> {
    return this.api.get<Nacionalidad[]>('/api/v1/cat/nacionalidades');
  }

  registrar(solicitud: SolicitudRegistro): Promise<Result<CuentaCreada>> {
    return this.api.post<CuentaCreada>('/api/v1/layaway/cliente', solicitud, { timeoutMs: 30_000 });
  }
}
