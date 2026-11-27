# Gesto Pago Web

Frontend web (Angular 22, standalone, TypeScript estricto, SCSS, PWA) de la Plataforma de Pagos de Servicios. Consume únicamente el backend propio (Spring Boot); no habla con Gesto Pago.

## Comandos

```
npm install
npm start                      # desarrollo (usa environment.development.ts)
npm run build                  # producción (usa environment.ts)
npx ng test --watch=false      # pruebas unitarias (Vitest)
```

## Configuración de la URL del backend

La URL base sale de `src/environments/`:

- `environment.development.ts`: `apiBaseUrl = http://localhost:9095`.
- `environment.ts` (producción): `apiBaseUrl` trae el marcador `https://REEMPLAZAR-URL-BASE-DEL-BACKEND.invalid`.
- **Content-Security-Policy:** `src/index.html` (desarrollo) y `src/index.prod.html` (producción) declaran la CSP. Su
  `connect-src` debe ser la misma URL que `apiBaseUrl`: al desplegar, reemplace también el marcador en `index.prod.html`.
- Las fuentes (Libre Franklin y Source Serif 4) se sirven desde la propia aplicación (`@fontsource`): el navegador no
  contacta a Google Fonts.
  **Antes de desplegar**, reemplázalo por la URL real del backend (HTTPS, sin barra final) y ejecuta `npm run build`. Mientras quede el marcador (o una URL `http://`), `npm run build` se detiene con un aviso (`scripts/verificar-url-produccion.mjs`). La URL se fija en tiempo de compilación; no hay variables de entorno en tiempo de ejecución. No incluyas secretos ni credenciales en estos archivos.

## Arquitectura

```
src/app/
  core/      config, http (ApiClient/Result), interceptors, guards, error, session
  shared/ui/ componentes reutilizables
  features/  auth, catalogo, referencia, pago, historial
    <feature>/data (dto, api, repository-impl)
              domain (models, repository, use-cases)
              presentation (pages, components, store)
```

Estado actual: las cinco secciones están construidas por capas — `auth` (login/logout), `catalogo` (directorio, filtros, detalle), `referencia` (captura y validación), `pago` (resumen, confirmación con PIN, resultado y comprobante) e `historial` (lista y detalle).

## Seguridad y sesión

- El token se guarda SOLO en memoria (`SessionStore`, Signals); nunca en localStorage, sessionStorage, IndexedDB ni cookies. Recargar la página cierra la sesión.
- Inactividad: 5 min sin peticiones autenticadas exitosas cierran la sesión localmente y redirigen a `/login`.
- Un 401 en una petición autenticada limpia la sesión y redirige a `/login` sin reintentar.
- `Authorization: Bearer` solo se envía a URLs que empiezan por `apiBaseUrl`.
- `ngsw-config.json` cachea el app shell, los estáticos y, en un `dataGroup`, los logotipos públicos de los proveedores (7 días, 100 entradas). Ninguna respuesta del API se guarda en Cache Storage.
