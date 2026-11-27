import {
  ESTADOS_VALIDACION,
  MOTIVOS_VALIDACION,
  MotivoValidacion,
  SITUACIONES_SALDO,
  SituacionSaldo,
  VERIFICACIONES,
  ValidacionReferencia,
  Verificacion,
} from '../../domain/models/validacion-referencia';

export interface ValidarReferenciaRequestDto {
  idSer: number;
  idProd: number;
  referencia: string;
}

export interface ValidacionReferenciaDto {
  estado: string;
  motivo: string | null;
  situacion: string | null;
  saldo: number | null;
  idValidacion: string | null;
  vigenciaSegundos: number | null;
  puedePagar: boolean;
  verificacion?: string | null;
  montoAPagar?: number | null;
  idSer: number;
  idProd: number;
  referencia: string;
  servicio: string | null;
  producto: string | null;
}

function enumeradoO<T extends string>(valor: string | null | undefined, validos: readonly T[]): T | null {
  return validos.find((v) => v === valor) ?? null;
}

/** Devuelve null si la respuesta no tiene la forma del contrato. */
export function aValidacion(dto: ValidacionReferenciaDto | null | undefined): ValidacionReferencia | null {
  const estado = enumeradoO(dto?.estado, ESTADOS_VALIDACION);
  if (!dto || estado === null) return null;
  return {
    estado,
    motivo: enumeradoO<MotivoValidacion>(dto.motivo, MOTIVOS_VALIDACION),
    situacion: enumeradoO<SituacionSaldo>(dto.situacion, SITUACIONES_SALDO),
    saldo: typeof dto.saldo === 'number' ? dto.saldo : null,
    idValidacion: dto.idValidacion || null,
    vigenciaSegundos: typeof dto.vigenciaSegundos === 'number' ? dto.vigenciaSegundos : null,
    puedePagar: dto.puedePagar === true,
    verificacion: enumeradoO<Verificacion>(dto.verificacion, VERIFICACIONES) ?? 'EN_LINEA',
    montoAPagar: typeof dto.montoAPagar === 'number' ? dto.montoAPagar : null,
    idSer: dto.idSer,
    idProd: dto.idProd,
    referencia: dto.referencia,
    servicio: dto.servicio ?? '',
    producto: dto.producto ?? '',
  };
}
