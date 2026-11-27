import { Failure } from '../http/result';

/**
 * Mensaje de UI para los fallos que no dependen del contexto.
 *
 * Regla del proyecto: la pantalla solo muestra textos amigables escritos aquí, elegidos por el tipo de fallo.
 * Nunca se muestra `Failure.mensaje` ni ningún texto, código o detalle técnico que venga del backend o de una excepción.
 */
export function mensajeGenerico(falla: Failure): string {
  switch (falla.tipo) {
    case 'red':
      return 'No pudimos conectarnos. Revisa tu conexión a internet e inténtalo de nuevo.';
    case 'servidor':
      return 'Por ahora no podemos atenderte. Inténtalo de nuevo en unos minutos.';
    case 'demasiados-intentos':
      return falla.retryAfterSegundos !== undefined
        ? `Demasiados intentos. Espera ${falla.retryAfterSegundos} segundos e inténtalo de nuevo.`
        : 'Demasiados intentos. Espera un momento e inténtalo de nuevo.';
    case 'no-autenticado':
      return 'Tu sesión ya no es válida. Inicia sesión de nuevo.';
    case 'prohibido':
      return 'No tienes permiso para realizar esta acción.';
    case 'no-encontrado':
      return 'No se encontró la información solicitada.';
    case 'validacion':
      return 'Revisa los datos que ingresaste.';
    default:
      return 'Algo salió mal. Inténtalo de nuevo.';
  }
}
