import { NombreIcono } from '../../../../shared/ui/icono/icono';
import { normalizarTexto } from '../../domain/models/producto-reglas';

/** Icono de una categoría (sin color propio). */
export interface VisualCategoria {
  icono: NombreIcono;
}

/**
 * Mapa único de presentación: palabras del nombre normalizado → icono. El orden importa
 * ("recargas" contiene "gas", por eso va antes que gas). No toca el dominio.
 */
const MAPA: [RegExp, VisualCategoria][] = [
  [/luz|electric|energia/, { icono: 'rayo' }],
  [/agua/, { icono: 'gota' }],
  [/recarga|celular|tiempo aire|movil|tarjetas de regalo/, { icono: 'movil' }],
  [/(^|\s)gas(\s|$)/, { icono: 'flama' }],
  [/telefon|fija/, { icono: 'telefono' }],
  [/internet|cable|tv|television|streaming/, { icono: 'senal' }],
  [/envio|dinero|transferencia/, { icono: 'enviar' }],
  [/consulta/, { icono: 'buscar' }],
  [/otros/, { icono: 'mas' }],
];

const DESCONOCIDA: VisualCategoria = { icono: 'recibo' };

export function visualDeCategoria(nombre: string | null | undefined): VisualCategoria {
  const texto = normalizarTexto(nombre ?? '');
  return MAPA.find(([patron]) => patron.test(texto))?.[1] ?? DESCONOCIDA;
}
