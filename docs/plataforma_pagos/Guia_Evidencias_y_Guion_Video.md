# Guía de Evidencias y Guion de Video

Estado documentado: **2026-11-14**. Cada prueba indica su estado real. Los nombres de pantallas, botones y textos se tomaron del código de la web (`web/src/app`) y de la app móvil (`mobile/lib`). Los datos de acceso se indican con marcadores: `<usuario>`, `<contraseña>`, `<PIN>`, `<referencia con formato válido>`.

## Antes de grabar

1. **Levantar Redis y después el backend, una sola vez.** No reiniciar el backend durante la sesión de grabación: cada arranque puede pedir un token a Gesto Pago y reiniciar muchas veces provocó el rechazo de operaciones con `CODIGO 55` (ver `Documento_Tecnico.md`, sección 8). Redis es necesario: guarda el catálogo y los comprobantes de la validación de referencia.
2. Backend: `.\gradlew.bat bootRun` (http://localhost:8081) con las variables de entorno definidas (`Documento_Tecnico.md`, 4.7 y 7.1). `APP_CORS_ORIGINS` debe incluir `http://localhost:4200` y `http://localhost:5000`.
3. Web: `cd web; npm start` (http://localhost:4200).
4. Móvil: la app probada es la compilada para web: `cd mobile; flutter run -d chrome --web-port 5000 --dart-define=API_BASE_URL=http://localhost:8081` (http://localhost:5000). No hay APK ni prueba en emulador Android: no lo muestres como hecho.
5. **Sesión única por usuario.** Iniciar sesión en una plataforma invalida la sesión de la otra. Graba y prueba en secuencia: primero la web completa, luego el móvil; para la prueba 10, vuelve a iniciar sesión al cambiar de plataforma y espera ese cierre.
6. **No repitas pagos ni consultas** para no saturar a Gesto Pago: el catálogo se lee de la caché del backend (no fuerces la sincronización) y basta un intento de pago por plataforma. Si un pago queda en "Estamos confirmando tu pago", no lo repitas.
7. Cliente de prueba local con cuenta activa, saldo y PIN: `<usuario>` / `<contraseña>` / `<PIN>`. Cuida que en pantalla no aparezcan contraseñas, PIN, tokens ni llaves; el campo PIN es de tipo contraseña.
8. Convención de nombres de evidencia: `P<nn>_<plataforma>_<descripcion>.png`. Toda captura debe mostrar la URL o la barra de la app.

## Resumen de estado

| # | Prueba | Estado al 2026-11-14 |
|---|---|---|
| 1 | Login web | Verificada el 2026-11-14 |
| 2 | Login móvil, mismo usuario | Verificada el 2026-11-14 (app Flutter compilada para web; Android sin probar) |
| 3 | Catálogo dinámico en ambas | Verificada el 2026-11-14 |
| 4 | Logotipos reales | Verificada el 2026-11-14 |
| 5 | Referencia correcta validada | No demostrable en el ambiente actual: la consulta en línea no está disponible |
| 6 | Referencia incorrecta rechazada y flujo bloqueado | Verificada con reserva: solo el formato incorrecto se bloquea |
| 7 | Confirmación explícita y pago | Verificada con reserva: la confirmación con PIN funciona; el pago lo rechaza el proveedor |
| 8 | Resultado y folio | No demostrable en el ambiente actual: no hay pago exitoso ni folio; el resultado Rechazado sí se ve |
| 9 | Historial del usuario | Verificada el 2026-11-14 |
| 10 | Sincronización entre plataformas | Verificada el 2026-11-14 (con pagos rechazados) |

Limitaciones del ambiente de Gesto Pago que explican las reservas: `verifyReference` responde siempre `CODIGO 05 "Origen Valido"`, y los dos pagos de prueba (referencia el 2026-11-13, recarga por teléfono el 2026-11-14) fueron rechazados con `CODIGO 55 "Origen Valido"`, con reintegro del saldo. La documentación oficial no describe esos códigos.

---

## Prueba 1. Login en la Web

- **Estado:** Verificada el 2026-11-14.
- **Objetivo:** un usuario válido inicia sesión en la web y llega al catálogo; uno inválido recibe un mensaje amigable.
- **Precondiciones:** backend y web en ejecución; cliente `<usuario>` / `<contraseña>`.
- **Pasos:**
  1. Abrir `http://localhost:4200`. Sin sesión redirige a `/login` ("Iniciar sesión", con la frase "Paga tus servicios con tu referencia.").
  2. Escribir un usuario o contraseña incorrectos y pulsar **Iniciar sesión**: aparece "Usuario o contraseña incorrectos."
  3. Escribir `<usuario>` y `<contraseña>` correctos y pulsar **Iniciar sesión** (el botón muestra "Ingresando…").
  4. Verificar que llega a `/catalogo` ("Catálogo de servicios") y que la cabecera muestra el nombre del cliente.
  5. En DevTools > Application confirmar que localStorage, sessionStorage, IndexedDB y cookies no contienen el token.
  6. Pulsar **Cerrar sesión** en la cabecera: vuelve a `/login`. Intentar abrir `/catalogo` redirige otra vez a `/login`.
- **Resultado esperado:** `POST /api/v1/auth/login` responde 200 con `codigo: 0`; con credenciales incorrectas responde 401 y la pantalla muestra solo el texto amigable; el cierre llama a `POST /api/v1/auth/logout`.
- **Evidencia:** captura de `/login` con el error; captura de `/catalogo` autenticado; Network con el login 200 (ocultando el valor del token); Application sin token almacenado; captura tras cerrar sesión.

## Prueba 2. Login en la app móvil con el mismo usuario

- **Estado:** Verificada el 2026-11-14 con la app Flutter compilada para web en `http://localhost:5000`. Sin APK ni prueba en emulador Android (faltan componentes del SDK en el equipo).
- **Objetivo:** el mismo `<usuario>` inicia sesión en Flutter.
- **Precondiciones:** backend en ejecución con CORS para `http://localhost:5000`; app abierta con `--dart-define=API_BASE_URL=http://localhost:8081`; la sesión web de la prueba 1 ya cerrada (sesión única).
- **Pasos:**
  1. Abrir la app: sin sesión muestra el login ("Gesto Pago", "Inicia sesión para continuar").
  2. Probar credenciales incorrectas y ver el mensaje amigable.
  3. Escribir `<usuario>` en **Usuario** y `<contraseña>` en **Contraseña** (el icono del ojo muestra u oculta la contraseña) y pulsar **Iniciar sesión** ("Iniciando sesión...").
  4. Verificar que llega al catálogo ("Catálogo de servicios").
  5. Pulsar el icono **Cerrar sesión** de la barra superior: regresa al login.
  6. (Opcional) Abrir la app sin `API_BASE_URL`: muestra la pantalla de error de configuración.
- **Resultado esperado:** mismo comportamiento que en la web. En la versión compilada para web el token solo vive en memoria; en Android se guardaría en `flutter_secure_storage` (no observado).
- **Evidencia:** capturas del login, del error y del catálogo tras entrar; salida de `flutter analyze` (sin avisos) y `flutter test` (178 pruebas); Network o log del backend con el login desde la app.

## Prueba 3. Catálogo dinámico en ambas plataformas

- **Estado:** Verificada el 2026-11-14: 901 servicios reales servidos desde la caché del backend.
- **Objetivo:** web y Flutter muestran el mismo catálogo, leído del backend (no fijo en el código), con categorías y buscador.
- **Precondiciones:** catálogo ya cargado en el backend (no forzar la sincronización con Gesto Pago); sesión iniciada.
- **Pasos:**
  1. Web, `/catalogo`: ver el contador ("N servicios encontrados"), las pestañas de categoría (**Todas** y una por categoría) y el campo **Buscar servicio o producto**. Escribir, por ejemplo, "luz" o "recarga"; usar **Quitar filtros** si no hay resultados.
  2. Abrir un servicio: pantalla de detalle con categoría, importe, descripción, "Datos del servicio" y botón **Continuar**.
  3. Móvil, "Catálogo de servicios": repetir búsqueda y categorías (la lista se actualiza deslizando hacia abajo).
  4. Comparar el total y los nombres entre ambas.
  5. Mostrar `GET /productos` en Swagger (`/swagger-ui.html`) o en Network como origen de los datos.
- **Resultado esperado:** ambas listas coinciden con la respuesta de `GET /productos`; el buscador y las categorías filtran.
- **Evidencia:** capturas del catálogo en ambas, de una búsqueda y de la respuesta de `/productos`.

## Prueba 4. Logotipos reales

- **Estado:** Verificada el 2026-11-14 en web y móvil.
- **Objetivo:** cada servicio muestra su logotipo oficial.
- **Precondiciones:** prueba 3 completada.
- **Pasos:**
  1. Ver el catálogo en ambas plataformas y confirmar que los logotipos cargan (origen `.../sistema/images/gestopago/servicios/{idServicio}.png`, URL que entrega el backend en `logoUrl`).
  2. En la pestaña Network de la web, filtrar por imágenes y mostrar las respuestas 200.
  3. Revisar un servicio sin logotipo: debe mostrar un marcador de respaldo (iniciales), nunca una imagen rota.
- **Resultado esperado:** logotipos reales visibles; sin imágenes rotas.
- **Evidencia:** capturas con logotipos en web y móvil; Network con las imágenes.
- **Nota:** los logotipos se cargan directo del dominio de Gesto Pago y la PWA los cachea 7 días (`ngsw-config.json`).

## Prueba 5. Referencia correcta validada

- **Estado:** No demostrable en el ambiente actual: `verifyReference` del ambiente de pruebas responde siempre `CODIGO 05 "Origen Valido"`, así que la consulta de adeudo en línea no está disponible y nunca aparece un saldo.
- **Objetivo (lo que debería demostrar con consulta en línea):** una referencia válida se valida contra Gesto Pago y muestra el adeudo; se emite un comprobante de un solo uso que habilita el pago.
- **Qué sí se puede mostrar hoy (validación por formato):** con `pagos.referencia.permitir-sin-consulta=true` el backend responde `verificacion: SOLO_FORMATO`, el usuario captura el importe de su recibo y se habilita el pago.
- **Precondiciones:** sesión iniciada; un servicio que admita referencia (`tipoFront` 2 o 4); `<referencia con formato válido>` (letras y números, sin espacios).
- **Pasos:**
  1. En el detalle del servicio pulsar **Continuar**. Aparece "Referencia de pago" con el indicador de pasos Servicio, Referencia, Confirmación.
  2. Si el servicio tiene imagen de ayuda, abrir **¿Dónde encuentro mi referencia?**
  3. Escribir `<referencia con formato válido>` y pulsar **Validar** ("Validando…", "Estamos verificando tu referencia…").
  4. Observar el resultado: "No podemos consultar el adeudo de este servicio. Revisa que tu referencia sea correcta antes de continuar." y el botón **Continuar al pago** habilitado.
- **Resultado esperado hoy:** el aviso de solo formato y el pago habilitado. **No** debe presentarse como validación en línea.
- **Resultado esperado con consulta en línea (no observado):** "Referencia verificada. Tienes un adeudo de $X." y "Total a pagar: $X".
- **Evidencia:** captura del aviso en web y móvil; Network con `POST /api/v1/pagos/referencia/validar` (200, `verificacion: SOLO_FORMATO`, `idValidacion` con vigencia de 300 s; ocultar el valor del comprobante si se muestra).
- **Reserva a decir en el video:** la validación en línea depende de que Gesto Pago la habilite.

## Prueba 6. Referencia incorrecta rechazada y flujo bloqueado

- **Estado:** Verificada con reserva: una referencia **mal formada** se detecta y bloquea el flujo; una referencia **bien formada pero inexistente** pasa como válida mientras no haya consulta en línea (el rechazo llegaría después, en el envío del pago).
- **Objetivo:** una referencia incorrecta no deja avanzar al pago.
- **Precondiciones:** las de la prueba 5.
- **Pasos:**
  1. Dejar la referencia vacía o escribir una con espacios o símbolos (por ejemplo `12 34-5`) y pulsar **Validar**: aparece "Ingresa tu referencia." o "La referencia solo admite letras y números, sin espacios."
  2. Comprobar que **Continuar al pago** está deshabilitado. Intentar abrir directamente la ruta `.../pago` de ese producto: redirige a la captura de referencia.
  3. Corregir con `<referencia con formato válido>`, pulsar **Validar** y confirmar que el botón se habilita.
  4. (Con consulta en línea, no observado) una referencia inexistente respondería "No encontramos esa referencia. Revísala e inténtalo de nuevo." y mantendría el botón bloqueado.
- **Resultado esperado:** mensaje amigable y botón bloqueado; no se llama al envío del pago.
- **Evidencia:** captura del error de formato y del botón deshabilitado; captura del desbloqueo; Network sin `POST /api/v1/pagos/servicio`.
- **Reserva a decir en el video:** el candado en servidor exige además un comprobante vigente y de un solo uso (428 si falta); eso está cubierto por pruebas automatizadas del backend.

## Prueba 7. Confirmación explícita y pago

- **Estado:** Verificada con reserva: el flujo completo (captura, resumen, PIN, confirmación) se recorrió en web (2026-11-13) y móvil (2026-11-14); Gesto Pago rechazó ambos pagos con `CODIGO 55` y el saldo se reintegró. No se obtuvo un cobro exitoso.
- **Objetivo:** el pago solo se envía tras un resumen y una confirmación explícita con PIN, con una sola petición por intento.
- **Precondiciones:** pasos previos de las pruebas 5 o, para productos de teléfono, el detalle del servicio; cuenta con saldo; `<PIN>`.
- **Pasos:**
  1. Servicio de referencia: tras la validación, el paso "Datos del pago" pide **Importe de tu recibo**; pulsar **Continuar**. Servicio de teléfono: capturar **Número de teléfono** y **Confirma el número de teléfono** (10 dígitos) y pulsar **Continuar**.
  2. En **Resumen del pago** revisar Teléfono o Referencia, Importe, Cuenta de cargo, Saldo disponible, Saldo después y Total. Pulsar **Pagar**.
  3. En "Confirma tu pago" pulsar **Cancelar** una vez: no hay cobro.
  4. Volver a **Pagar**, escribir `<PIN>` (4 dígitos) y pulsar **Confirmar pago** ("Procesando tu pago…"). Los botones quedan deshabilitados durante el envío.
  5. (Opcional) Escribir un PIN incorrecto: "PIN incorrecto. Te quedan N intentos." A los 3 fallos: "PIN bloqueado" por 15 minutos; no hacerlo en la grabación.
- **Resultado esperado:** una sola petición `POST /api/v1/pagos/servicio` con `Idempotency-Key` nueva por intento y `twoFA`; HTTP 200 (CONFIRMADA), 202 (pendiente) o 422 (rechazada). En el ambiente actual: 422 rechazada.
- **Evidencia:** capturas del resumen, del modal de confirmación (sin mostrar el PIN) y de "Procesando tu pago…"; Network con `Idempotency-Key` y la respuesta (ocultar PIN y tokens); saldo antes y después.

## Prueba 8. Resultado y folio

- **Estado:** No demostrable en el ambiente actual para el **folio** y el estado Exitoso o Pendiente, porque no se ha observado ningún pago exitoso ni pendiente con las apps nuevas. El resultado **Rechazado** sí se observó (dos veces) con el reintegro del saldo. La lógica de folio, Exitoso y Pendiente está cubierta por pruebas automatizadas (web 191, móvil 178), no por una operación real.
- **Objetivo:** mostrar el resultado (Exitoso / Rechazado / Pendiente) y el folio o número de operación.
- **Precondiciones:** prueba 7 ejecutada.
- **Pasos y resultado observado hoy (Rechazado):**
  1. Tras **Confirmar pago**, aparece el ticket con sello **NO APLICADO**, "No se realizó el pago", "No se hizo ningún cargo a tu cuenta." y un botón de acción (por ejemplo **Intentar de nuevo**) más **Ver mis pagos**.
  2. Verificar que el saldo de la cuenta no cambió (reintegro).
  3. Abrir **Ver mis pagos**: la operación aparece como Rechazado.
- **Qué mostraría un pago exitoso (no observado):** sello **PAGADO**, "¡Pago realizado!", "Tu pago de <servicio> por <monto> se aplicó correctamente. Folio: <folio>.", botón **Ver comprobante** (ticket con Servicio, Producto, Teléfono o Referencia, Cuenta, Fecha y hora, Estatus, Total y Folio) y **Ver mis pagos** / **Ir al inicio**.
- **Qué mostraría un pago pendiente (no observado):** sello **PENDIENTE**, "Estamos confirmando tu pago", botones **Consultar de nuevo** y **Ver mis pagos**, sin permitir repetir el cobro; el cliente consulta el estado cada 10, 20, 35, 50, 70, 90 y 120 s.
- **Evidencia:** capturas del resultado Rechazado en web y móvil; respuesta 422 del backend (sin texto del proveedor); saldo antes y después. No inventar capturas de folio.
- **Reserva a decir en el video:** el folio no pudo demostrarse porque el ambiente de pruebas de Gesto Pago rechazó las operaciones.
- **Excepción a señalar:** en un pago exitoso el comprobante muestra la `leyenda` del proveedor; es la única excepción a la regla de mensajes propios y está pendiente de decisión del responsable.

## Prueba 9. Historial del usuario

- **Estado:** Verificada el 2026-11-14 en web y móvil.
- **Objetivo:** el usuario ve sus pagos y el detalle de cada uno.
- **Precondiciones:** al menos una operación realizada (los rechazos también aparecen).
- **Pasos:**
  1. Web: pulsar **Historial** en la cabecera ("Mis pagos"). Móvil: pulsar el icono **Historial** de la barra del catálogo ("Mis pagos").
  2. Verificar la lista, de la más reciente a la más antigua, con logotipo, servicio, fecha, destino enmascarado, folio o número de operación, monto y estado (Exitoso / Rechazado / Pendiente).
  3. Probar los filtros **Todos**, **Exitosos**, **Rechazados** y **Pendientes**, y **Ver más** si hay más de una página.
  4. Abrir una operación: web "Detalle del pago" (`/historial/:upc`) con comprobante; móvil "Detalle del pago". En una pendiente aparece **Consultar de nuevo**.
  5. Con otro usuario (si existe), confirmar que no ve estas operaciones.
- **Resultado esperado:** solo operaciones del usuario autenticado; `POST /api/v1/pagos/historial` y `POST /api/v1/pagos/estado` responden 200.
- **Evidencia:** capturas de lista, filtros y detalle en ambas; respuesta del historial.

## Prueba 10. Sincronización entre plataformas

- **Estado:** Verificada el 2026-11-14: un pago hecho desde la web y otro desde el móvil aparecen en el historial de ambas plataformas. Los pagos fueron rechazados por Gesto Pago; se sincroniza el historial, no un folio.
- **Objetivo:** una operación hecha en una plataforma aparece en la otra con el mismo usuario, porque ambas leen del mismo backend.
- **Precondiciones:** pruebas 1, 2 y 9. Sesión única: iniciar sesión en cada plataforma cuando le toque, sabiendo que cierra la otra.
- **Pasos:**
  1. Iniciar sesión en la web y anotar el historial (**Actualizar** si hace falta).
  2. Realizar un pago desde la web (basta uno; no repetir).
  3. Cerrar sesión, iniciar sesión en el móvil con el mismo usuario y abrir "Mis pagos": debe aparecer la operación hecha en la web.
  4. Realizar un pago desde el móvil.
  5. Volver a la web, iniciar sesión y pulsar **Actualizar** en "Mis pagos": deben aparecer ambas operaciones.
- **Resultado esperado:** ambos historiales coinciden en servicio, monto, estado y número de operación.
- **Evidencia:** capturas del historial de cada plataforma antes y después, con el mismo identificador de operación.

---

## Guion de video

Duración total estimada: **17 a 19 minutos**. Se graba en secuencia (la sesión es única por usuario), no en pantalla dividida con sesiones simultáneas. Ocultar contraseñas, PIN y tokens. Decir con claridad las reservas de las escenas 8 a 11.

| Escena | Prueba | Mostrar | Decir | Duración |
|---|---|---|---|---|
| 1 | Intro | Diagrama de arquitectura de `Documento_Tecnico.md` (sección 2) | "La plataforma tiene una web PWA, una app Flutter y un backend que consulta Gesto Pago. Los clientes nunca hablan con Gesto Pago: sus credenciales y llaves AES no pueden estar en un navegador ni en una app." | 1:30 |
| 2 | Preparación | Terminal con Redis y backend arriba (sin variables visibles); web en `:4200`; app Flutter en `:5000` | "Todo corre localmente y levantamos el backend una sola vez, porque Gesto Pago pide un token por día. La sesión es única por usuario: grabamos una plataforma y luego la otra." | 1:00 |
| 3 | 1 | Web: login fallido, login correcto, catálogo, Application sin token, **Cerrar sesión** | "El token vive solo en memoria: recargar la página cierra la sesión. Tras 5 minutos de inactividad también." | 2:00 |
| 4 | 2 | App Flutter: login fallido y correcto con el mismo usuario; **Cerrar sesión** | "Mismo usuario, mismo backend. En Android el token iría a almacenamiento seguro; aquí probamos la app compilada para web, y todavía no la hemos probado en un emulador Android." | 1:30 |
| 5 | 3 | Catálogo web: contador, categorías, buscador, detalle; catálogo móvil; `GET /productos` en Swagger | "El catálogo no está en el código: son 901 servicios que el backend lee de su caché y sincroniza con Gesto Pago una vez al día." | 2:00 |
| 6 | 4 | Logotipos en ambas; Network con las imágenes | "Los logotipos reales vienen del dominio de Gesto Pago por `idServicio`; si falta uno se muestran las iniciales." | 1:00 |
| 7 | 5 | Captura de referencia, **Validar**, aviso "No podemos consultar el adeudo…" y botón habilitado | "El ambiente de pruebas de Gesto Pago no tiene la consulta de adeudo en línea, así que el backend valida el formato y el usuario captura el importe. La validación en línea con saldo no se pudo demostrar." | 1:30 |
| 8 | 6 | Referencia mal formada, error, botón bloqueado, intento de ir directo a `.../pago`; corrección | "Una referencia mal formada bloquea el flujo. Una bien formada pero inexistente hoy no se detecta hasta enviar el pago, por la limitación anterior. En el servidor el pago exige un comprobante de un solo uso." | 1:30 |
| 9 | 7 | Resumen del pago, **Cancelar**, **Pagar** otra vez, PIN, **Confirmar pago** | "El pago solo se envía tras el resumen y la confirmación con PIN; cada intento lleva una clave de idempotencia para no cobrar dos veces." | 2:00 |
| 10 | 8 | Resultado **NO APLICADO** y saldo reintegrado; mostrar en pruebas automatizadas los casos de folio y pendiente | "Gesto Pago rechazó nuestros pagos de prueba y el saldo se reintegró. No hemos visto un pago exitoso con folio ni uno pendiente en las apps nuevas; esos casos están cubiertos por pruebas automatizadas, 191 en la web y 178 en móvil, no por una operación real." | 2:00 |
| 11 | 9 | Historial, filtros y detalle en web y móvil | "El historial sale de nuestra base de datos, porque Gesto Pago no ofrece historial. Pendiente y rechazado se distinguen con sellos y etiquetas." | 1:30 |
| 12 | 10 | Pago en la web, historial en el móvil, pago en el móvil, historial en la web (con inicio de sesión en cada cambio) | "Como ambas consumen el mismo backend, las operaciones se ven en las dos plataformas." | 2:00 |
| 13 | Cierre | `Documento_Tecnico.md`, sección 8, y resultados de pruebas: 767 de backend, 191 de web y 178 de móvil | "Las pruebas automatizadas pasan. Quedan pendientes: un pago exitoso con folio, la validación en línea, la prueba en Android y rotar las credenciales que están en archivos versionados." | 1:30 |

Nota: no repetir pagos para rehacer una escena; si una toma falla, reutilizar la operación ya existente en el historial.
