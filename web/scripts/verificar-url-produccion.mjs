// Impide compilar la versión de producción con la URL marcadora del backend:
// una build así se publicaría rota (todas las llamadas irían a un dominio .invalid).
// Corre antes de `npm run build` (script "prebuild").
import { readFileSync } from 'node:fs';

const MARCADOR = 'REEMPLAZAR-URL-BASE-DEL-BACKEND';
const archivos = ['src/environments/environment.ts', 'src/index.prod.html'];
const pendientes = archivos.filter((f) => readFileSync(new URL(`../${f}`, import.meta.url), 'utf8').includes(MARCADOR));

if (pendientes.length > 0) {
  console.error(
    `\nFalta la URL real del backend en: ${pendientes.join(', ')}.\n` +
      'Reemplace el marcador por la URL https del backend (apiBaseUrl y connect-src de la CSP) y vuelva a compilar.\n' +
      'Para desarrollo use `npm start` o `ng build --configuration development`.\n',
  );
  process.exit(1);
}
const env = readFileSync(new URL('../src/environments/environment.ts', import.meta.url), 'utf8');
if (/apiBaseUrl:\s*'http:\/\//.test(env)) {
  console.error('\napiBaseUrl de producción debe usar https.\n');
  process.exit(1);
}
