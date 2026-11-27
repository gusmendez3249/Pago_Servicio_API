/** Textos del historial. Regla del proyecto: se escriben aquí; nunca se muestra texto, código ni detalle del backend. */
export const TITULO_HISTORIAL = 'Mis pagos';
export const TEXTO_SIN_PAGOS = 'Aún no tienes pagos';
export const TEXTO_SIN_PAGOS_FILTRO = 'No hay pagos con ese estado';
export const TEXTO_ERROR_LISTA = 'No pudimos cargar tus pagos. Inténtalo de nuevo.';
export const TEXTO_ERROR_MAS = 'No pudimos cargar más pagos. Inténtalo de nuevo.';
export const TEXTO_NO_ENCONTRADA = 'No encontramos esa operación';
export const TEXTO_ERROR_DETALLE = 'No pudimos cargar la operación. Inténtalo de nuevo.';
export const TEXTO_PENDIENTE_DETALLE = 'Aún no recibimos la confirmación de esta operación. No vuelvas a pagar.';

export const FILTROS_ESTADO = [
  { valor: 'todos', etiqueta: 'Todos' },
  { valor: 'exitosos', etiqueta: 'Exitosos' },
  { valor: 'rechazados', etiqueta: 'Rechazados' },
  { valor: 'pendientes', etiqueta: 'Pendientes' },
] as const;
