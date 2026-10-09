const { chromium } = require("playwright-core");
const fs = require("fs");
const path = require("path");

const SALIDA = process.argv[2];
const BASE = "http://localhost:8081";
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
altaError.datosPersonales.nombre = "Li";            // menos de 3 caracteres
altaError.datosPersonales.sexo = "masculino";       // catálogo estricto: solo MASCULINO
altaError.datosPersonales.curp = "MERG951315HGTNDS09"; // mes 13
altaError.datosContacto.telefonoMovil = 551234567;  // 9 dígitos

const casos = [
  { archivo: "01_cliente_exito_201.png", ruta: "/api/v1/layaway/cliente", cuerpo: texto(alta) },
  { archivo: "02_cliente_error_400.png", ruta: "/api/v1/layaway/cliente", cuerpo: texto(altaError) },
  { archivo: "03_login_exito_200.png", ruta: "/api/v1/auth/login",
    cuerpo: JSON.stringify({ bandera: 1, username: "gustavo.mendez", password: "Password123!" }, null, 2) },
  { archivo: "04_login_error_401.png", ruta: "/api/v1/auth/login",
    cuerpo: JSON.stringify({ bandera: 1, username: "gustavo.mendez", password: "ContrasenaIncorrecta1" }, null, 2) },
  { archivo: "05_catalogo_nacionalidades_exito_200.png", ruta: "/api/v1/cat/nacionalidades", cuerpo: null },
];

(async () => {
  const browser = await chromium.launch({
    executablePath: "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    headless: true,
  });
  const ctx = await browser.newContext({ viewport: { width: 880, height: 1000 }, deviceScaleFactor: 2, locale: "es-MX" });
  const page = await ctx.newPage();

  async function abrirSwagger() {
    await page.goto(`${BASE}/swagger-ui.html`, { waitUntil: "networkidle" });
    await page.waitForSelector(".swagger-ui .opblock", { timeout: 30000 });
  }

  // Vista general de endpoints
  await abrirSwagger();
  await page.screenshot({ path: path.join(SALIDA, "00_swagger_vista_general.png"), fullPage: false });
  console.log("OK 00_swagger_vista_general.png");

  const cortes = {};
  const filtro = process.argv[3]; // opcional: solo los casos cuyo archivo empiece con este prefijo
  for (const c of casos) {
    if (filtro && !c.archivo.startsWith(filtro)) continue;
    await abrirSwagger();
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
    // (se omite la tabla estática de documentación de códigos que sigue debajo)
    // Coordenadas ABSOLUTAS de la página (getBoundingClientRect es relativo a la ventana: se suma el scroll)
    const clip = await page.evaluate(({ ruta }) => {
      const abs = (el) => { const r = el.getBoundingClientRect(); return { x: r.left + scrollX, y: r.top + scrollY, w: r.width, h: r.height }; };
      const bloque = [...document.querySelectorAll(".opblock")].find((b) => b.querySelector(`.opblock-summary-path[data-path="${ruta}"]`));
      const caja = abs(bloque);
      const resp = abs(bloque.querySelector(".live-responses-table"));
      const curl = abs(bloque.querySelector(".curl-command"));
      // corte = inicio de la etiqueta "Curl" (≈ 40 px por encima del bloque), relativo al recorte
      return { x: caja.x, y: caja.y, width: caja.w, height: resp.y + resp.h - caja.y + 12, corteCss: curl.y - 40 - caja.y };
    }, { ruta: c.ruta });
    const { corteCss, ...recorte } = clip;
    await page.screenshot({ path: path.join(SALIDA, c.archivo), clip: recorte, fullPage: true });
    cortes[c.archivo] = Math.round(corteCss * 2); // px de la imagen final (deviceScaleFactor = 2)
    console.log(`OK ${c.archivo}  -> HTTP ${estado.split(/\s/)[0]}`);
  }
  if (process.argv[4]) fs.writeFileSync(process.argv[4], JSON.stringify(cortes, null, 2));
  await browser.close();
})().catch((e) => { console.error("FALLO", e); process.exit(1); });
