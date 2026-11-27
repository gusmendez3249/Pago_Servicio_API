# Reporte de QA — BanGus / Pago de Servicios API

**Fecha:** 26 de noviembre de 2026
**Alcance:** backend Spring Boot (`localhost:9095`), web Angular (`localhost:4200`), Redis y PostgreSQL locales. El móvil (Flutter) se revisó solo en código y pruebas unitarias.
**Enfoque:** pruebas de caja negra contra la API en ejecución (seguridad, validación, concurrencia, carga, latencia y tolerancia a fallos), complementadas con revisión de código para explicar la causa de cada falla.

> Restricción respetada: Gesto Pago penaliza el exceso de peticiones, así que las pruebas se diseñaron para cortarse **antes** de llamar al proveedor. Solo hubo **1 llamada real a `sendTx`** (prueba de idempotencia concurrente), que respondió 55 y se reintegró correctamente.

---

## 1. Resumen ejecutivo

| Severidad | Cantidad |
|---|---|
| Alta | 4 |
| Media | 7 |
| Baja | 6 |
| Informativa | 2 |

La aplicación **resiste bien los ataques clásicos**: no se logró inyección SQL, acceso a datos de otro cliente (IDOR), escalar privilegios, saltarse el PIN, ni cobrar dos veces con la misma Idempotency-Key, incluso con peticiones simultáneas.

Donde **sí truena** es en **escalabilidad y tolerancia a fallos**:

1. El catálogo público (`GET /productos`) es lento y pesado; con 100 usuarios simultáneos tarda **6 s** en responder y es un vector de denegación de servicio trivial.
2. Si **Redis se cae**, cada consulta al catálogo se queda esperando **5 s**; una ráfaga de 250 peticiones agota los hilos del servidor y **frena toda la API**. Además, los pagos por referencia dejan de funcionar.
3. El registro es público y **regala $1,000** a cada cuenta nueva: es dinero gratis ilimitado.
4. Cualquiera puede **bloquear la cuenta de otro usuario** fallando la contraseña 5 veces.

---

## 2. Hallazgos

### ALTA

#### QA-01 · El registro público regala $1,000 por cuenta (fraude de saldo)
- **Qué pasa:** `POST /api/v1/layaway/cliente` (bandera 1) no requiere autenticación y cada alta abre una cuenta con **saldo inicial de $1,000.00**. El único freno es el límite de 20 altas por minuto e IP.
- **Evidencia:** se crearon 5 clientes de prueba sin ningún control; cada uno recibió $1,000.00. Con 20 altas/min por IP son **$1,728,000 por día y por IP**.
- **Impacto:** si el saldo se usa para pagar servicios reales con Gesto Pago, es pérdida directa de dinero.
- **Recomendación:** saldo inicial en $0 (o solo en ambientes de prueba, controlado por propiedad), alta de clientes solo por ejecutivo o con verificación de identidad (correo/SMS), y CAPTCHA en el registro.

#### QA-02 · `GET /productos` es un vector de DoS: 580 KB, sin compresión ni paginación, ~550 ms por petición
- **Evidencia (medida):**

  | Escenario | Throughput | p50 | p95 |
  |---|---|---|---|
  | 1 petición aislada | — | 520–700 ms | — |
  | 20 concurrentes | 19.8 req/s | 960 ms | 1,188 ms |
  | 100 concurrentes | 16.4 req/s | **5,960 ms** | **7,427 ms** |

  La respuesta pesa **580,572 bytes** y no se comprime aunque el cliente envíe `Accept-Encoding: gzip`. El endpoint es público (sin token).
- **Causa (código):** `ProductosStorageServiceImpl.consultarRedis()` usa `redisRepository.findAll()`, que en Spring Data Redis hace un `SMEMBERS` más **un `HGETALL` por producto (902 viajes a Redis por petición)**. Luego `ProductosController` filtra `tipoFront` **en memoria**, después de traer todo.
- **Recomendación:**
  - Guardar el catálogo completo como **un solo valor** (JSON o lista) en Redis, o cachearlo en memoria (Caffeine) con invalidación en la sincronización diaria.
  - Activar `server.compression.enabled=true` (el JSON se reduce a ~10 %).
  - Agregar `ETag`/`Cache-Control: public, max-age` (el catálogo cambia una vez al día).
  - Paginar o filtrar en el origen.

#### QA-03 · Redis caído = API completa degradada (sin circuit breaker)
- **Evidencia (medida con Redis detenido):**
  - Cada `GET /productos` tarda **5.0 s** (`spring.data.redis.timeout=5000ms`) antes de caer a PostgreSQL. El throughput baja a **3.9 req/s**.
  - Con una ráfaga de 250 peticiones al catálogo, el resto de la API (`/pagos/historial`, que no usa Redis) subió de **14 ms a 4,468 ms**: los 200 hilos de Tomcat quedaron esperando a Redis.
  - `actuator/health` sigue diciendo `UP`, porque la salud de Redis está desactivada a propósito, así que el balanceador no se entera.
- **Recomendación:**
  - Timeout de Redis corto (100–300 ms).
  - **Circuit breaker** (Resilience4j): tras N fallos, ir directo a PostgreSQL o a una caché en memoria durante un tiempo.
  - Limitar la concurrencia del catálogo (bulkhead) para que no consuma todos los hilos.

#### QA-04 · Redis es punto único de falla de los pagos con referencia
- **Evidencia:** con Redis detenido, `POST /api/v1/pagos/referencia/validar` (CFE, idSer 166) respondió **503 PROVEEDOR_NO_DISPONIBLE**. Sin comprobante de validación no se puede pagar luz, agua, etc.
- **Contradicción:** `application.properties` describe Redis como "caché opcional del catálogo", pero los comprobantes de referencia **solo** viven ahí.
- **Recomendación:** guardar el comprobante en PostgreSQL (tabla con TTL), o usar Redis con réplica/Sentinel y documentar que es infraestructura crítica.

### MEDIA

#### QA-05 · Cualquiera puede bloquear la cuenta de otro (DoS de cuenta)
- **Evidencia:** 5 contraseñas incorrectas contra el usuario `qa.xtsrspuk` → el login con la contraseña **correcta** responde **423 "Cuenta bloqueada… 14 min"**. Con el límite de 10 intentos/min por IP, un atacante mantiene bloqueada a la víctima indefinidamente (5 intentos cada 15 min).
- **Recomendación:** bloqueo progresivo (retraso creciente en lugar de bloqueo duro), bloqueo por par usuario+IP, CAPTCHA tras 3 fallos y aviso al titular.

#### QA-06 · Error 500 al pedir una página muy grande del historial
- **Evidencia:** `POST /api/v1/pagos/historial` con `{"pagina":2147483647,"tamanio":10}` → **500**. Log: `InvalidDataAccessApiUsageException: Page offset exceeds Integer.MAX_VALUE`.
- **Recomendación:** validar `pagina` con `@Max` (p. ej. 10,000) o calcular `pagina*tamanio` antes de consultar, y responder 400.

#### QA-07 · No se cruzan los datos de identidad (KYC débil)
- **Evidencia:** se registró un cliente con CURP `…900101H…` (nacido el 1990-01-01, hombre) pero con `fechaNacimiento` 1985-06-15, `sexo` FEMENINO y RFC genérico `XAXX850615AB1`. Respuesta: **201 Creado**.
- **Recomendación:** validar que la fecha y el sexo de la CURP coincidan con los capturados, que el RFC comparta los primeros 10 caracteres con la CURP, rechazar RFC genéricos (`XAXX`, `XEXX`) y validar el dígito verificador de la CURP.

#### QA-08 · Los límites de tasa por IP castigan a usuarios legítimos y no escalan
- **Evidencia:** con una sola IP, el login se bloqueó (429) tras 10 peticiones y los pagos tras 20, **sumando a todos los usuarios**. Detrás de un NAT (escuela, oficina, datos móviles con CGNAT) todos comparten esos 10 logins/min.
- Los contadores viven **en memoria**: con dos instancias el límite real se duplica, y se reinician con cada despliegue.
- **Recomendación:** limitar por usuario o cuenta además de por IP, mover los contadores a Redis (bucket4j-redis) y subir los umbrales por IP.

#### QA-09 · Un Gesto Pago lento puede agotar los hilos del servidor
- **Evidencia:** 10 pagos simultáneos con la misma llave tardaron **5.0 s**. `gestopago.sendtx.read-timeout=65000`: si el proveedor se cuelga, cada pago retiene un hilo de Tomcat hasta 65 s. Con 200 hilos bastan ~200 pagos en curso para que nadie más pueda usar la API.
- **Recomendación:** bulkhead (pool acotado y separado para llamadas a Gesto Pago), responder 202 INDETERMINADA en cuanto se agote el cupo, y circuit breaker hacia el proveedor.

#### QA-10 · Datos personales en los logs por los errores de validación
- **Evidencia:** Spring registra cada `MethodArgumentNotValidException` con nivel WARN e incluye el valor rechazado (`rejected value [...]`). Un registro con correo o teléfono mal formados deja ese dato en claro en el log.
- **Recomendación:** bajar `org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver` a ERROR, o desactivar `spring.mvc.log-resolved-exception`, y registrar solo campo y regla.

#### QA-11 · Dependencias desactualizadas y librerías sin uso
- Spring Boot **3.3.6** ya está fuera del soporte OSS (la rama 3.3 dejó de recibir parches públicos).
- `commons-lang3:3.12.0` y `commons-fileupload:1.5` tienen avisos de seguridad publicados en 2025 (recursión no controlada y DoS por partes multipart, respectivamente). **Verificar con un escaneo actualizado.**
- Hay librerías que el código no usa y que amplían la superficie de ataque: `ojdbc8`, `ucp`, `spring-boot-starter-mail`, `freemarker`, `opennlp-tools`, `google-api-client`, `jjwt`, `java-jwt`, `jwks-rsa`.
- El plugin `org.owasp.dependencycheck 8.1.2` es antiguo y ya no funciona con la API actual de NVD.
- Web: `npm audit --omit=dev` → **0 vulnerabilidades**.
- **Recomendación:** subir a Spring Boot 3.5.x, eliminar las dependencias sin uso, actualizar dependency-check a 12.x y correrlo en CI.

### BAJA

| ID | Hallazgo | Evidencia | Recomendación |
|---|---|---|---|
| QA-12 | Swagger UI y `/api-docs` públicos (44 KB con todos los endpoints) | `GET /swagger-ui.html` → 200 | `SWAGGER_ENABLED=false` en producción (riesgo aceptado por el equipo) |
| QA-13 | Redis sin contraseña y escuchando en todas las interfaces (`bind * -::*`) | `CONFIG GET requirepass` vacío; `protected-mode yes` lo mitiga solo en local | `requirepass` y `bind 127.0.0.1` en local; TLS y ACL en producción |
| QA-14 | La consulta de ejecutivo (bandera 4) devuelve **todos** los clientes con CURP, RFC, correo y teléfono, sin paginar | 33 clientes, 10 KB, 32 ms | Paginación obligatoria y campos enmascarados en el listado |
| QA-15 | La web no define Content-Security-Policy y carga Google Fonts (la IP del usuario llega a Google) | `index.html` sin CSP | CSP en el servidor web y fuentes autoalojadas; mencionarlo en el aviso de privacidad |
| QA-16 | La sincronización del catálogo hace `deleteAll()` y luego `saveAll()` en Redis: durante ese lapso el catálogo queda vacío o parcial | Revisión de `ProductosStorageServiceImpl.reemplazarEnRedis` | Escribir en una llave nueva y cambiar el puntero de forma atómica (`RENAME`) |
| QA-17 | El PIN se valida **antes** que producto y monto: una petición con monto incorrecto gasta un intento de PIN | Monto $1.00 para un producto de $15 → 403 PIN_INCORRECTO | Validar catálogo y monto antes del PIN |

### INFORMATIVA

- **QA-18 ·** En pagos se acepta el monto entero `15`, pero en el registro `ingresoMensual` exige exactamente dos decimales (`10000.00`). Conviene unificar la regla.
- **QA-19 ·** Cada petición autenticada hace un `UPDATE` en `tb_usuario_login` para renovar la sesión. A 300 req/s funciona, pero es escritura constante en la tabla de usuarios; se puede renovar solo si pasó más de 1 minuto.

---

## 3. Controles que pasaron (lo que no se pudo romper)

| Área | Prueba | Resultado |
|---|---|---|
| Autenticación | Sin token, token basura, esquema Basic, `Bearer` vacío, token de 10,000 caracteres | 401 en todos |
| Sesión | Usar el token después de `logout` | 401 |
| IDOR | Cliente A paga con la cuenta y el PIN de B | 404 (no revela que la cuenta existe) |
| IDOR | Cliente A consulta el estado de un pago de otro usuario | 404 |
| IDOR | Cliente A consulta los datos de B (bandera 4 con filtro) | 404; sin filtros solo ve su propio registro |
| Privilegios | Cliente usa conciliación o baja (solo ejecutivo) | 403 / 400 |
| Inyección | SQL, XSS, 5,000 caracteres y byte nulo en el usuario; SQL en Idempotency-Key y upc | 400 por formato; nunca llega a la BD |
| Fuerza bruta de PIN | 3 fallos → bloqueo 15 min (el PIN correcto también se rechaza) | Correcto |
| Carrera de PIN | 10 PIN incorrectos **simultáneos** | Solo 2 cuentan como incorrectos y 8 salen bloqueados: no se puede adivinar en paralelo |
| Fuerza bruta de login | Límite por IP | 429 con `Retry-After` |
| Registro masivo | 25 altas seguidas | 429 a partir de la 20 |
| Idempotencia | 10 pagos **simultáneos** con la misma llave | 1 llega a Gesto Pago y 9 reciben 409; un reintento devuelve el mismo resultado y la misma llave con otros datos da 409 |
| Saldo | Pago rechazado por Gesto Pago (55) | Saldo reintegrado ($1,000.00) |
| Validación | Monto negativo, cero, 3 decimales, 1e308, NaN, string; campos extra (`esAdmin`, `saldo`); llave JSON duplicada; JSON de 5,000 niveles; XML | 400 / 415 |
| Tamaño | Cuerpo de 2 MB | 413 |
| CORS | Origen `https://evil.example` y `null` | 403 sin `Access-Control-Allow-Origin` |
| Métodos | TRACE, PUT, DELETE, PATCH | 405 |
| Exposición | `/actuator`, `/actuator/env`, `/actuator/heapdump`, path traversal | 404 |
| Cabeceras | CSP, HSTS, nosniff, DENY, Permissions-Policy, COOP/CORP, `no-store` | Presentes |
| Errores | Ninguna respuesta expuso stack trace, SQL ni texto del proveedor | Correcto |

---

## 4. Métricas de carga (local, 1 instancia, cliente en la misma máquina)

| Endpoint | Peticiones | Concurrencia | req/s | p50 | p95 | p99 | Errores |
|---|---|---|---|---|---|---|---|
| `GET /actuator/health` | 2,000 | 100 | 437 | 140 ms | 334 ms | 416 ms | 0 |
| `GET /api/v1/cat/nacionalidades` | 2,000 | 50 | 333 | 125 ms | 247 ms | 310 ms | 0 |
| `POST /api/v1/cuentas/mias` (auth) | 1,000 | 50 | 326 | 125 ms | 207 ms | 246 ms | 0 |
| `POST /api/v1/pagos/historial` (auth) | 1,000 | 50 | 379 | 110 ms | 186 ms | 230 ms | 0 |
| `POST /api/v1/pagos/historial` (auth) | 2,000 | 200 | 302 | 192 ms | 418 ms | 565 ms | 0 |
| `GET /productos` | 300 | 20 | **19.8** | **960 ms** | 1,188 ms | 1,219 ms | 0 |
| `GET /productos` | 500 | 100 | **16.4** | **5,960 ms** | 7,427 ms | 7,569 ms | 0 |
| `GET /productos` **sin Redis** | 100 | 20 | **3.9** | **5,037 ms** | 5,139 ms | 5,146 ms | 0 |

Las APIs autenticadas aguantan bien: ~300 req/s y p95 por debajo de 420 ms con 200 concurrentes, sin errores. El cuello de botella es el catálogo.

---

## 5. Prioridad sugerida de corrección

1. **QA-01** saldo regalado en el registro (riesgo económico directo).
2. **QA-02 + QA-03** catálogo: una sola llave en Redis o caché en memoria, gzip, timeout corto y circuit breaker. Resuelve también la mayor parte del problema de latencia.
3. **QA-04** sacar los comprobantes de referencia de Redis o volverlo de alta disponibilidad.
4. **QA-05, QA-06, QA-08, QA-09** robustez de login, historial, límites de tasa y llamadas al proveedor.
5. **QA-07, QA-10, QA-11** KYC, logs y dependencias.
6. Bajas e informativas.

---

## 6. Datos que dejó la prueba en la base local

- Clientes de prueba creados por el registro público: `qa.miseoszy`, `qa.nulhvjmy`, `qa.jvlwaxrv`, `qa.xtsrspuk` y un cliente con nombre "Incongruente" (usuario `qa.incong…`). Cada uno tiene $1,000.00.
- `qa.nulhvjmy` y `qa.xtsrspuk` quedaron con el PIN o el login bloqueados 15 minutos.
- Una operación `RECHAZADA` con motivo `ERROR_GESTOPAGO` (upc `G9GFRT6BTX`) de `qa.miseoszy`, con saldo reintegrado.

---

## 7. Correcciones aplicadas y re-test (26-nov-2026)

Todos los hallazgos se corrigieron. Las 796 pruebas del backend, las 200 de la web y las migraciones (sobre un PostgreSQL limpio) pasan. El re-test se hizo contra la app en ejecución.

| ID | Corrección | Re-test |
|---|---|---|
| QA-01 | El saldo inicial es configurable (`APP_SALDO_INICIAL`) y vale **0.00 por defecto**. En producción no debe definirse | Prueba unitaria: el registro da $0.00 |
| QA-02 | El catálogo vive en **una sola llave** de Redis, en **caché en memoria** (10 min, se invalida al sincronizar), con **gzip**, **ETag** y `Cache-Control: public, max-age=300` | 20 → **208 req/s**; p50 con 100 concurrentes: 5,960 → **470 ms**; 580 KB → **43 KB**; segunda descarga → **304** |
| QA-03 | Timeout de Redis de 5 s a **300 ms**; **circuit breaker** (30 s directo a PostgreSQL); se sirve la última copia buena si todo falla | Sin Redis: `/productos` p95 **117 ms** (antes 5,139 ms); historial durante 250 peticiones: máx **99 ms** (antes 4,468 ms) |
| QA-04 | Los comprobantes de referencia tienen **respaldo en PostgreSQL** (`tb_comprobante_referencia`, V18), con consumo atómico `DELETE … RETURNING` | Sin Redis: `validar` → **200** (antes 503) |
| QA-05 | Bloqueo de login por **usuario + IP** (5 fallos, 15 min). El bloqueo global de la cuenta subió a 20 fallos, solo contra la fuerza bruta distribuida | Pruebas: el atacante queda bloqueado y el titular entra desde su IP |
| QA-06 | `pagina` ≤ 10,000 | **400** (antes 500) |
| QA-07 | En el registro se valida el dígito verificador de la CURP, la fecha y el sexo contra la CURP, y el RFC contra la CURP; se rechaza el RFC genérico. En la actualización, la fecha y el sexo deben coincidir con la CURP guardada | **400** con errores en `fechaNacimiento`, `sexo` y `rfc` |
| QA-08 | Contadores en **Redis** compartidos entre instancias (respaldo en memoria). Pagos y validaciones se cuentan **por sesión**, con un tope de 300/min por IP. Login: 30/min por IP | Misma IP: la sesión E se limita en 20 y la sesión F sigue operando |
| QA-09 | **Bulkhead** (50 pagos simultáneos hacia Gesto Pago) y **circuit breaker** (5 fallos seguidos → pausa de 30 s), ambos **antes** de reservar saldo (503 sin cargo) | Pruebas unitarias y de servicio |
| QA-10 | `spring.mvc.log-resolved-exception=false` y los resolvers de Spring en nivel ERROR | Un registro con correo y teléfono inválidos **no deja el dato en el log** |
| QA-11 | Spring Boot **3.3.6 → 3.5.16**, Spring Cloud 2025.0.3, springdoc 2.8.17, dependency-check 12.2.2. Se eliminaron 18 dependencias sin uso (Oracle, mail, freemarker, cache, JWT, commons-*, httpclient5, gson, org.json, google-api-client, JAXB, OpenNLP, config-client) | Compila y pasan las 796 pruebas |
| QA-12 | Swagger **apagado por defecto** (`SWAGGER_ENABLED=false`); en local lo enciende el arranque | — |
| QA-13 | Redis local con `requirepass` y `bind 127.0.0.1 -::1` (`redis-local.conf`, ignorado por git); la API lee `REDIS_PASSWORD` | Sin credenciales: `NOAUTH Authentication required` |
| QA-14 | Consulta de ejecutivo **paginada** (50 por página, `pagina`) y **enmascarada** en el listado general (CURP, RFC, correo, teléfono, cuenta). La búsqueda por llave sigue mostrando el registro completo | Prueba unitaria |
| QA-15 | Web con **Content-Security-Policy** (`index.html` y `index.prod.html`), fuentes autoalojadas con `@fontsource` y sin CSS crítico en línea | El navegador solo contacta la web, la API y los logos de Gesto Pago; las fuentes cargan |
| QA-16 | Reemplazo atómico: Redis con `SET` en llave temporal + `RENAME`; PostgreSQL en una transacción. La sincronización ahora **siempre** actualiza PostgreSQL (antes solo si Redis fallaba) | Pruebas unitarias |
| QA-17 | El producto y el monto se validan **antes** del PIN | Monto incorrecto con PIN malo → **400 MONTO_NO_PERMITIDO**, sin gastar intento |
| QA-18 | Regla unificada: `ingresoMensual` admite **hasta** 2 decimales, igual que el monto del pago | — |
| QA-19 | La sesión se renueva en base de datos como máximo **una vez por minuto** | — |

**Pendientes fuera del alcance de este reporte:**
- La rama 3.5 de Spring Boot es la última 3.x. Conviene planear la migración a 4.x.
- El móvil usa `google_fonts`, que descarga las fuentes de Google en tiempo de ejecución; conviene empaquetarlas como assets.
- Antes de desplegar la web hay que reemplazar el marcador de `connect-src` en `index.prod.html`, igual que `apiBaseUrl`.
