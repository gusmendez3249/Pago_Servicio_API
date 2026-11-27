/**
 * Configuración de PRODUCCIÓN: backend desplegado en Render (render.yaml, servicio pago-servicios-api).
 * Si cambia, actualice también connect-src de la CSP en src/index.prod.html.
 */
export const environment = {
  produccion: true,
  apiBaseUrl: 'https://pago-servicios-api.onrender.com',
};
