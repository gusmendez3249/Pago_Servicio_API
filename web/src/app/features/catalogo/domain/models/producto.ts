/** Producto del catálogo de servicios (los campos opcionales pueden venir nulos). */
export interface Producto {
  servicio: string;
  producto: string;
  idServicio: number | null;
  idProducto: number | null;
  idCatTipoServicio: number | null;
  tipoFront: number | null;
  hasDigitoVerificador: boolean;
  precio: number | null;
  showAyuda: boolean;
  tipoReferencia: string | null;
  legend: string | null;
  logoUrl: string | null;
  ayudaUrl: string | null;
  categoria: string | null;
}
