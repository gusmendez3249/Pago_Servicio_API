/*
 * Capturas de Swagger UI con peticiones reales (Try it out -> Execute), un caso de éxito y uno de error por endpoint.
 * Las operaciones protegidas (banderas 2, 3 y 4) usan el botón "Authorize" de Swagger con un token real.
 *
 * Uso (API en http://localhost:8081, arrancada con APP_EJECUTIVO_USUARIO / APP_EJECUTIVO_PASSWORD):
 *   npm install playwright-core
 *   EJECUTIVO_USUARIO=ejecutivo.admin EJECUTIVO_PASSWORD='Ejecutivo#2026' node capturas_swagger.js <carpeta_salida> [prefijo] [cortes.json]
 * Requiere Microsoft Edge (se ajusta la ruta de EDGE si es otra).
 */
const { chromium } = require("playwright-core");
const fs = require("fs");
const path = require("path");

const SALIDA = process.argv[2];
const filtro = process.argv[3];          // opcional: solo los casos cuyo archivo empiece con este prefijo
const CORTES = process.argv[4];          // opcional: archivo donde se guardan los puntos de corte (para el Word)
const BASE = "http://localhost:8081";
const EDGE = process.env.EDGE || "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const USUARIO_EJ = process.env.EJECUTIVO_USUARIO || "ejecutivo.admin";
const PASSWORD_EJ = process.env.EJECUTIVO_PASSWORD || "Ejecutivo#2026";
fs.mkdirSync(SALIDA, { recursive: true });

const alta = {
  bandera: 1,
  datosPersonales: {
    nombre: "Gustavo", segundoNombre: "Adolfo", apellidoPaterno: "Mendez", apellidoMaterno: "Rodriguez",
    fechaNacimiento: "1995-05-15", curp: "MERG950515HGTNDS09", rfc: "MERG950515AB1",
    sexo: "MASCULINO", nacionalidad: "MEXICANA", estadoCivil: "SOLTERO",
  },
  datosContacto: { correo: "gustavo.mendez@example.com", telefonoMovil: 4181234567, telefonoAlt: 4187654321 },
  domicilio: {
    calle: "Hidalgo", numeroExterior: "123", numeroInterior: "A", colonia: "Centro",
    municipio: "Dolores Hidalgo", estado: "Guanajuato", codigoPostal: "37800", pais: "MEXICO",
  },
  informacionLaboral: { ocupacion: "Desarrollador de Software", empresa: "Tech Corp", ingresoMensual: "__INGRESO__" },
  loginCredenciales: { username: "gustavo.mendez", password: "Password123!", faceIdBiometrico: 987654321 },
};
// ingresoMensual debe viajar como número con 2 decimales exactos (15000.00)
const texto = (o) => JSON.stringify(o, null, 2).replace('"__INGRESO__"', "15000.00");

const altaError = JSON.parse(JSON.stringify(alta));
altaError.datosPersonales.nombre = "Li";               // menos de 3 caracteres
altaError.datosPersonales.sexo = "masculino";          // catálogo estricto: solo MASCULINO
altaError.datosPersonales.curp = "MERG951315HGTNDS09"; // mes 13
altaError.datosContacto.telefonoMovil = 551234567;     // 9 dígitos
altaError.loginCredenciales.password = "password";     // incumple la política de contraseña

// Segundo cliente: sirve para demostrar que un cliente no puede consultar el registro de otro
const alta2 = JSON.parse(JSON.stringify(alta));
alta2.datosPersonales.nombre = "Laura"; alta2.datosPersonales.segundoNombre = "Elena";
alta2.datosPersonales.apellidoPaterno = "Torres"; alta2.datosPersonales.apellidoMaterno = "Vargas";
alta2.datosPersonales.curp = "TOVL920821MGTRRR04"; alta2.datosPersonales.rfc = "TOVL920821KJ3";
alta2.datosPersonales.sexo = "FEMENINO";
alta2.datosContacto = { correo: "laura.torres@example.com", telefonoMovil: 4187770011 };
alta2.loginCredenciales = { username: "laura.torres", password: "Password123!" };

const json = (o) => JSON.stringify(o, null, 2);
const casos = [
  { archivo: "01_cliente_exito_201.png", ruta: "/api/v1/layaway/cliente", cuerpo: texto(alta) },
  { archivo: "02_cliente_error_400.png", ruta: "/api/v1/layaway/cliente", cuerpo: texto(altaError) },
  { archivo: "03_login_exito_200.png", ruta: "/api/v1/auth/login", cuerpo: json({ bandera: 1, username: "gustavo.mendez", password: "Password123!" }) },
  { archivo: "04_login_error_401.png", ruta: "/api/v1/auth/login", cuerpo: json({ bandera: 1, username: "gustavo.mendez", password: "ContrasenaIncorrecta1" }) },
  { archivo: "05_catalogo_nacionalidades_exito_200.png", ruta: "/api/v1/cat/nacionalidades", cuerpo: null },
  // Operaciones protegidas: antes de ejecutarlas se pega el token en el botón Authorize
  { archivo: "06_cliente_consulta_ejecutivo_exito_200.png", ruta: "/api/v1/layaway/cliente", cuerpo: json({ bandera: 4, filtros: { curp: "MERG95" } }), auth: "ejecutivo" },
  { archivo: "07_cliente_consulta_sin_token_error_401.png", ruta: "/api/v1/layaway/cliente", cuerpo: json({ bandera: 4, filtros: { curp: "MERG95" } }), auth: null },
  { archivo: "08_cliente_consulta_registro_ajeno_error_404.png", ruta: "/api/v1/layaway/cliente", cuerpo: json({ bandera: 4, filtros: { curp: "TOVL920821MGTRRR04" } }), auth: "cliente" },
];

async function api(ruta, cuerpo) {
  const r = await fetch(BASE + ruta, { method: "POST", headers: { "Content-Type": "application/json" }, body: typeof cuerpo === "string" ? cuerpo : JSON.stringify(cuerpo) });
  return { estado: r.status, cuerpo: await r.json() };
}

(async () => {
  const browser = await chromium.launch({ executablePath: EDGE, headless: true });
  const ctx = await browser.newContext({ viewport: { width: 880, height: 1000 }, deviceScaleFactor: 2, locale: "es-MX" });
  const page = await ctx.newPage();
  const cortes = {};
  const tokens = {};

  async function abrirSwagger() {
    await page.goto(`${BASE}/swagger-ui.html`, { waitUntil: "networkidle" });
    await page.waitForSelector(".swagger-ui .opblock", { timeout: 30000 });
  }

  async function autorizar(token) {
    await page.locator(".auth-wrapper button.authorize").first().click();
    await page.locator(".modal-ux input[type=text], .modal-ux input:not([type])").first().fill(token);
    await page.locator(".modal-ux .auth-btn-wrapper button.authorize").first().click();
    await page.locator(".modal-ux button.btn-done").first().click();
  }

  async function ejecutar(c) {
    await abrirSwagger();
    if (c.auth) await autorizar(tokens[c.auth]);
    const bloque = page.locator(`.opblock:has(.opblock-summary-path[data-path="${c.ruta}"])`).first();
    await bloque.locator(".opblock-summary").first().click();          // expandir
    await bloque.locator("button.try-out__btn").click();               // Try it out
    if (c.cuerpo !== null) {
      const area = bloque.locator("textarea.body-param__text");
      await area.fill(c.cuerpo);
      // Mostrar el cuerpo completo (sin barra de desplazamiento) para que la evidencia lo muestre íntegro
      await area.evaluate((el) => { el.style.height = (el.scrollHeight + 8) + "px"; el.scrollTop = 0; });
    }
    await bloque.locator("button.execute").click();                    // Execute
    await bloque.locator(".live-responses-table .response .response-col_status").first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(600);
    const estado = (await bloque.locator(".live-responses-table .response .response-col_status").first().innerText()).trim();
    // Captura desde el encabezado del endpoint hasta el final de la respuesta real del servidor
    // (se omite la tabla estática de documentación de códigos que sigue debajo).
    // Coordenadas ABSOLUTAS de la página (getBoundingClientRect es relativo a la ventana: se suma el scroll)
    const clip = await page.evaluate(({ ruta }) => {
      const abs = (el) => { const r = el.getBoundingClientRect(); return { x: r.left + scrollX, y: r.top + scrollY, w: r.width, h: r.height }; };
      const b = [...document.querySelectorAll(".opblock")].find((x) => x.querySelector(`.opblock-summary-path[data-path="${ruta}"]`));
      const caja = abs(b);
      const resp = abs(b.querySelector(".live-responses-table"));
      const curl = abs(b.querySelector(".curl-command"));
      // corte = inicio de la etiqueta "Curl" (≈ 40 px por encima del bloque), relativo al recorte
      return { x: caja.x, y: caja.y, width: caja.w, height: resp.y + resp.h - caja.y + 12, corteCss: curl.y - 40 - caja.y };
    }, { ruta: c.ruta });
    const { corteCss, ...recorte } = clip;
    await page.screenshot({ path: path.join(SALIDA, c.archivo), clip: recorte, fullPage: true });
    cortes[c.archivo] = Math.round(corteCss * 2); // px de la imagen final (deviceScaleFactor = 2)
    console.log(`OK ${c.archivo}  -> HTTP ${estado.split(/\s/)[0]}`);
  }

  // Vista general de endpoints (con el botón Authorize visible)
  await abrirSwagger();
  await page.screenshot({ path: path.join(SALIDA, "00_swagger_vista_general.png"), fullPage: false });
  console.log("OK 00_swagger_vista_general.png");

  const publicos = casos.filter((c) => c.auth === undefined);
  const protegidos = casos.filter((c) => c.auth !== undefined);

  for (const c of publicos) {
    if (filtro && !c.archivo.startsWith(filtro)) continue;
    await ejecutar(c);
  }

  // Segundo cliente y tokens frescos (cada inicio de sesión invalida el token anterior del mismo usuario)
  if (!filtro || protegidos.some((c) => c.archivo.startsWith(filtro))) {
    await api("/api/v1/layaway/cliente", texto(alta2)).catch(() => null);
    tokens.ejecutivo = (await api("/api/v1/auth/login", { bandera: 1, username: USUARIO_EJ, password: PASSWORD_EJ })).cuerpo.data.token;
    tokens.cliente = (await api("/api/v1/auth/login", { bandera: 1, username: "gustavo.mendez", password: "Password123!" })).cuerpo.data.token;
    for (const c of protegidos) {
      if (filtro && !c.archivo.startsWith(filtro)) continue;
      await ejecutar(c);
    }
  }

  if (CORTES) fs.writeFileSync(CORTES, JSON.stringify(cortes, null, 2));
  await browser.close();
})().catch((e) => { console.error("FALLO", e); process.exit(1); });
