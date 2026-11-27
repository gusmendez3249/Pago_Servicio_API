import { Producto } from '../../domain/models/producto';

/** Tal como llega del backend: cualquier campo puede venir nulo o ausente. */
export interface ProductoDto {
  servicio?: string | null;
  producto?: string | null;
  idServicio?: number | null;
  idProducto?: number | null;
  idCatTipoServicio?: number | null;
  tipoFront?: number | null;
  hasDigitoVerificador?: boolean | null;
  precio?: number | null;
  showAyuda?: boolean | null;
  tipoReferencia?: string | null;
  legend?: string | null;
  logoUrl?: string | null;
  ayudaUrl?: string | null;
  categoria?: string | null;
}

function textoOpcional(valor: string | null | undefined): string | null {
  const t = valor?.trim();
  return t ? t : null;
}

export function aProducto(dto: ProductoDto): Producto {
  return {
    servicio: dto.servicio ?? '',
    producto: dto.producto ?? '',
    idServicio: dto.idServicio ?? null,
    idProducto: dto.idProducto ?? null,
    idCatTipoServicio: dto.idCatTipoServicio ?? null,
    tipoFront: dto.tipoFront ?? null,
    hasDigitoVerificador: dto.hasDigitoVerificador ?? false,
    precio: dto.precio ?? null,
    showAyuda: dto.showAyuda ?? false,
    tipoReferencia: dto.tipoReferencia ?? null,
    legend: dto.legend ?? null,
    logoUrl: textoOpcional(dto.logoUrl),
    ayudaUrl: textoOpcional(dto.ayudaUrl),
    categoria: textoOpcional(dto.categoria),
  };
}
