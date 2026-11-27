/**
 * Configuración de PRODUCCIÓN: backend desplegado en Render (render.yaml, servicio bangus-api).
 * Si cambia, actualice también connect-src de la CSP en src/index.prod.html.
 */
export const environment = {
  produccion: true,
  apiBaseUrl: 'https://bangus-api.onrender.com',
};
