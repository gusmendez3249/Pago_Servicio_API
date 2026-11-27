# Revisión integral de QA — 27 de noviembre de 2026

Alcance: backend (Spring Boot), web (Angular) y móvil (Flutter), incluido lo agregado al final: crear cuenta en web y en móvil, y el panel de administrador para recargas.
Enfoque: ciberseguridad, datos personales, pruebas, arquitectura y despliegue.
Entorno: local (backend en 9095, PostgreSQL 18 y Redis con contraseña). El backend reutilizó el token de Gesto Pago guardado, así que no se pidió uno nuevo.

## 1. Resultado general

| Área | Resultado |
|---|---|
| Pruebas del backend | 802 pasan, 0 fallan, 1 omitida. Cobertura: 84.4 % de líneas y 78.5 % de ramas. |
| Pruebas de la web | 205 pasan (27 archivos) |
| Pruebas del móvil | 195 pasan; `flutter analyze` sin observaciones |
| `npm audit` de la web | 0 vulnerabilidades |
| Ataques en vivo | 36 pasan. Hay 2 fallas, que en realidad son el mismo hallazgo (H-2). |
| Logs | No aparecen contraseñas, tokens ni números de cuenta completos. |

**Veredicto:** la aplicación resiste bien los ataques directos. El riesgo más grave no está en el código, sino en una contraseña de administrador publicada en el repositorio público (H-1). Hay que corregirlo antes de desplegar.

## 2. Hallazgos

### H-1 · ALTA — La contraseña del administrador está publicada en el repositorio público

- El repositorio en GitHub es **público**.
- La contraseña del usuario ejecutivo inicial aparece en texto claro en estos archivos:
  - `README.md:61`
  - `docs/evidencias/pruebas_autorizacion.py`, en las líneas 14 y 31 (valor por defecto).
  - `docs/evidencias/pruebas_consulta_bandera4.py:27`.
  - `docs/evidencias/pruebas_seguridad_carga.py:35`.
  - `docs/evidencias/swagger/capturas_swagger.js`, en las líneas 7 y 20.
- El historial de git también conserva la contraseña de PostgreSQL de un `application.properties` anterior (se quitó en el commit `2b3ca43`).
- **Impacto:** con el panel nuevo, esa cuenta **abona dinero a cualquier cuenta**. Si el despliegue usa la misma contraseña, cualquiera que lea el repo puede crear saldo.
- **Corrección:**
  1. Cambiar la contraseña del ejecutivo y la de la base de datos. No basta con borrarlas del repo: el historial ya es público.
  2. Quitar los valores por defecto de los scripts para que exijan la variable de entorno, y usar un marcador en el README.
  3. Opcional: que el *seeder* rechace contraseñas que aparezcan en la documentación.

### H-2 · MEDIA — Se valida el cuerpo antes de comprobar la sesión

- Las rutas protegidas responden **400 con la lista de campos y reglas** a quien no tiene sesión o no tiene el rol, en lugar de 401 o 403.
- Comprobado sin token en:
  - `POST /personas`
  - `/api/v1/admin/recargas`
  - `/api/v1/admin/recargas/historial`
  - `/api/v1/pagos/conciliacion`
- Un cliente sin rol también recibe 400 en `/personas` y en `conciliacion`.
- **Impacto:** un anónimo puede reconstruir el contrato de las rutas de administrador (nombres de campos, tamaños y reglas). Además viola el principio de autenticar primero y luego procesar la entrada.
- **Corrección:** autenticar y revisar el rol en un `HandlerInterceptor` o un filtro, que corre antes de leer y validar el cuerpo. Así quedan 401 sin sesión y 403 sin rol, sin importar el cuerpo.

### H-3 · MEDIA — Las recargas no tienen tope diario ni doble aprobación

- Cada recarga tiene un máximo de $50,000, pero no hay límite por día, por ejecutivo ni por cuenta destino.
- El límite por minuto solo frena la velocidad.
- Una sesión de ejecutivo robada o un empleado deshonesto puede abonar sin tope.
- La auditoría existe (`tb_recarga_saldo`, con ejecutivo, monto y saldo resultante), pero nada la vigila.
- **Corrección:** fijar un tope diario por ejecutivo y por cuenta (configurable) y responder 422 al rebasarlo. Opcionalmente, pedir el PIN del ejecutivo para montos altos o una segunda aprobación.

### H-4 · MEDIA (despliegue móvil) — La versión release se firma con la llave de depuración y acepta HTTP

- `mobile/android/app/build.gradle.kts`: la versión `release` usa `signingConfigs.getByName("debug")`. Así no se puede publicar en Play Store, y cualquiera con la llave de depuración puede firmar una versión que la reemplace.
- `AppConfig.configuracionValida` acepta `http://` en cualquier compilación. Una release mal configurada mandaría el token y la contraseña sin cifrar.
- **Corrección:**
  1. Configurar una llave de firma propia, con `key.properties` fuera de git.
  2. Exigir `https` cuando `kReleaseMode` sea verdadero.

### H-5 · BAJA — La API responde con textos técnicos

- Ejemplos de respuestas:
  - "Datos no validos{monto=…}"
  - "Error de tipo de dato en el campo 'monto': Se esperaba el tipo BigDecimal."
- La web y el móvil no los muestran (solo enseñan mensajes amigables), pero quien llama directo a la API ve nombres de tipos de Java y el formato interno de los errores.
- **Corrección:** respuestas genéricas con `campo` y `motivo`, sin tipos de Java.

### H-6 · BAJA — Hay partes críticas con poca cobertura

| Clase | Cobertura de líneas | Por qué importa |
|---|---|---|
| `LimitadorTasa` | 41 % | Es el limitador con Redis y Lua que protege login, pagos y recargas. |
| `GlobalExceptionHandler` | 45 % | Decide qué texto sale en cada error. |
| `V16__hash_datos_biometricos` | 0 % | Migración que convierte el Face ID a hash; si falla en producción, nadie entra con Face ID. |
| `RespaldoComprobantesPostgres` | 0 % | Es el respaldo de comprobantes cuando Redis cae. |
| `ConciliacionPagoScheduler` | 0 % | Concilia los pagos pendientes con Gesto Pago. |

### H-7 · BAJA — Contraseña real de la cuenta de prueba en una prueba del móvil

- `mobile/test/features/registro/registro_screen_test.dart:120-121` usa la misma contraseña que la cuenta local de prueba de `.env.pruebas`.
- Al hacer commit quedaría pública.
- **Corrección:** usar otro valor ficticio que cumpla la política.

### H-8 · BAJA — Pendientes de configuración para desplegar (ya conocidos)

- `web/src/environments/environment.ts` y `index.prod.html` (`connect-src`) tienen la URL marcadora `REEMPLAZAR-URL-BASE-DEL-BACKEND.invalid`.
- El móvil usa `google_fonts`, que descarga fuentes de Google al abrir la app. Esto expone la IP del usuario a un tercero y falla sin conexión.
- En el servidor: definir `REDIS_PASSWORD` y `CLIENT_IP_HEADER` según el proxy, y **no** definir `APP_SALDO_INICIAL`.

## 3. Lo que se probó y resistió

**Autenticación y roles**
- Sin token, con un token falso o con un token ya cerrado (*logout*), la respuesta es 401.
- Un cliente que llama a las rutas de administrador recibe 403.
- Login: el mismo mensaje para un usuario inexistente que para una contraseña mala, así que no se pueden enumerar usuarios.
- Fuerza bruta: el usuario se bloquea (423) a partir del sexto intento fallido.

**Acceso a datos de otros (IDOR) y campos extra (*mass assignment*)**
- Un cliente no puede pagar desde una cuenta ajena ni consultar su historial.
- Si se agregan campos como `saldo` a una recarga, se rechazan con 400.
- Si el JSON trae una clave repetida (`monto` dos veces), se rechaza.

**Validación de montos de recarga**
- Se rechazan con 400: 0, negativos, más de $50,000, 3 decimales, notación científica, texto y NaN.
- Sin Idempotency-Key, la respuesta es 400. Si la cuenta no existe, es 404.

**Inyección**
- SQL y Log4Shell en el login y en el número de cuenta se rechazan con 400 por patrón.
- XSS en el nombre del registro se rechaza.
- La web no usa `innerHTML` ni `bypassSecurityTrust`.

**Abuso del servidor**
- Un cuerpo de 2 MB recibe 413.
- Un JSON anidado 5,000 niveles recibe 400.
- Un JSON malformado recibe 400, sin *stacktrace*.

**Cabeceras y exposición**
- Cabeceras presentes: HSTS, CSP `default-src 'none'`, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer` y `Cache-Control: no-store`.
- No hay cabecera `Server` ni `X-Powered-By`.
- CORS rechaza un origen ajeno (403, sin `Access-Control-Allow-Origin`).
- `/actuator/*`, `/h2-console`, `/v3/api-docs`, `/.env` y `secretos.properties` no están expuestos.

**Secretos y datos personales**
- `secretos.properties`, `.env.pruebas`, `redis-local.conf` y `*.rdb` están en `.gitignore`. Las llaves de Gesto Pago (API key, secret e IV) nunca estuvieron en git.
- Los tokens se guardan solo en memoria en la web y en almacenamiento seguro en el móvil (`flutter_secure_storage`).
- La contraseña, el PIN y el Face ID se guardan con PBKDF2.

**Arquitectura**
- Las recargas son idempotentes y el abono es atómico, condicionado a que la cuenta esté ACTIVA, en la misma transacción que la auditoría.
- Las llamadas a Gesto Pago tienen *bulkhead* y *circuit breaker*.
- El limitador de peticiones sigue funcionando en memoria si Redis cae.

## 4. No se ejecutó

- **OWASP dependency-check del backend:** necesita descargar la base de NVD (horas sin una API key). Spring Boot 3.5.16 y Spring Cloud 2025.0.3 son versiones con soporte vigente.
- **Ataques que mueven dinero real:** recarga válida y pagos. La idempotencia y la concurrencia ya se probaron en la revisión del 26 de noviembre; esta vez no se modificaron saldos.
- **Pruebas en un dispositivo Android físico.**

## 5. Correcciones aplicadas y re-prueba

| Hallazgo | Corrección | Verificación |
|---|---|---|
| H-1 | La contraseña se quitó del `README.md` y de los 4 scripts de `docs/evidencias`: los scripts ahora exigen `EJECUTIVO_PASSWORD`. `EjecutivoInicialSeeder` rota el hash cuando cambia `APP_EJECUTIVO_PASSWORD` y cierra la sesión del ejecutivo. También rechaza las contraseñas publicadas, de las que solo guarda el SHA-256. La contraseña local ya se rotó en `secretos.properties`. | En vivo, la contraseña publicada responde 401 y la nueva entra. Hay 3 pruebas unitarias nuevas. |
| H-2 | `AutenticacionPreviaInterceptor` autentica antes de leer el cuerpo. En `/api/v1/admin/**`, `/personas*` y `/pagos/conciliacion` además exige rol EJECUTIVO. | En vivo, sin sesión las 6 rutas responden 401 aunque el cuerpo sea inválido, y un cliente recibe 403 en las 3 rutas de administrador. |
| H-3 | Tope diario por cuenta (100,000) y por ejecutivo (200,000), configurables y con respuesta 422. Se bloquean la fila del ejecutivo y la de la cuenta durante la recarga. La web muestra un mensaje amigable. | Pruebas con H2: rechazo sin abonar y 10 recargas simultáneas de $30 con tope de $100, de las que se aplican exactamente 3. |
| H-4 | La release de Android se firma con `android/key.properties` y nunca con la llave de depuración. En release el móvil exige `https`, salvo hosts locales. | `app_config_test.dart` |
| H-5 | Mensaje genérico "Datos no válidos. Revise los campos indicados.", con el detalle por campo en `data`. Ya no se nombran tipos de Java. | En vivo: "El campo 'monto' tiene un valor no válido para su tipo." |
| H-6 | Pruebas nuevas para `LimitadorTasa`, `GlobalExceptionHandler`, la migración V16, `RespaldoComprobantesPostgres`, `ConciliacionPagoScheduler` y el interceptor. | Cobertura de líneas por clase: LimitadorTasa 95 %, GlobalExceptionHandler 91 %, V16 100 %, Respaldo 88 %, Scheduler 100 %. Cobertura total: 88.5 % de líneas y 82.2 % de ramas. |
| H-7 | La prueba del móvil usa una contraseña ficticia. | Se buscaron las contraseñas de `.env.pruebas` en todos los archivos del repo y no aparece ninguna. |
| H-8 | `npm run build` se detiene si queda la URL marcadora o una URL `http://` (`web/scripts/verificar-url-produccion.mjs`). El `README.md` tiene ahora una lista de despliegue. | Comprobado: la compilación de producción se detiene con un aviso y la de desarrollo compila. |

**Re-prueba total:**
- Backend: 848 pruebas pasan, 0 fallan.
- Web: 207 pruebas pasan.
- Móvil: 197 pruebas pasan; `flutter analyze` sin observaciones.

**Pendiente, fuera del código:**
- **Contraseña de PostgreSQL:** cambiarla en el servidor y en `secretos.properties`. La que aparece en el historial de git ya no debe usarse.
- ~~Fuentes del móvil~~: resuelto. Libre Franklin 400–700 y Source Serif 4 600 vienen en `mobile/assets/fonts/` (verificadas por SHA-256, con su licencia OFL) y `allowRuntimeFetching = false`. En el navegador los 5 TTF se sirven desde la app y no hay peticiones a `fonts.gstatic.com`.
