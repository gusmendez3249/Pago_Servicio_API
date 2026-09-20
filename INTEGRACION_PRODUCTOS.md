# Documentación de Integración: Endpoint de Productos GestoPago

## 1. Objetivo
El objetivo de esta integración es consumir el servicio externo de GestoPago para obtener el catálogo de productos disponibles (`GET /sistema/service/getProductList.do`), procesar la respuesta en formato XML a través de Jackson XML, mapearla a nuestro DTO interno (`ProductoResponse`) y exponerla a los clientes de nuestra plataforma mediante el endpoint `GET /productos` envuelta en un formato estandarizado `GenericResponse`.

---

## 2. Endpoints y Configuración

### Endpoints
- **Endpoint Interno Lista Plana (Filtro opcional)**: `GET /productos` o `GET /productos?tipoFront=1`
- **Endpoint Interno Agrupado por tipoFront**: `GET /productos/agrupados`
- **Endpoint Externo (Proveedor)**: `GET /sistema/service/getProductList.do`
- **Endpoint Autenticación Proveedor**: `POST /sistema/app/jwt-gp/authenticate/`
- **URL Base Proveedor**: `https://gestopago.portalventas.net`

### Propiedades de Configuración (`application.properties`)
```properties
# Base URL GestoPago
gestopago.base-url=https://gestopago.portalventas.net

# Autenticación GestoPago
gestopago.auth.id-distribuidor=83
gestopago.auth.codigo-dispositivo=GPS83-TPV-17
gestopago.auth.password=12345678
gestopago.auth.refresh-rate-ms=3600000

# Endpoint y Timeouts de Productos
gestopago.productos.endpoint=/sistema/service/getProductList.do
gestopago.productos.connect-timeout=5000
gestopago.productos.read-timeout=10000
```

---

## 3. Flujo de Autenticación, Almacenamiento y Renovación de Token

1. **Obtención de Token**: El cliente de autenticación `GestoPagoAuthClient` solicita el JWT invocando `POST /sistema/app/jwt-gp/authenticate/` con `idDistribuidor`, `codigoDispositivo` y `password`.
2. **Persistencia y Actualización**: `GestoPagoTokenServiceImpl` guarda o actualiza la entidad `GestoPagoToken` mediante `GestoPagoTokenRepository` y `GestoPagoTokenMapper`.
3. **Mantenimiento Programado**: Se ejecuta `@Scheduled(fixedRateString = "${gestopago.auth.refresh-rate-ms:3600000}")` para renovar el token proactivamente cada hora.
4. **Renovación On-Demand**: Cuando `ProductosServiceImpl` consulta el token activo a `GestoPagoTokenService` y no existe uno presente, invoca automáticamente `tokenService.renovarToken()` antes de realizar la petición HTTP externa.
5. **Formato Header**: La petición externa incluye `Authorization: Bearer <token>`. Si el atributo `token_type` del proveedor viene vacío o nulo, se utiliza `"Bearer"` como valor predeterminado.

---

## 4. Estructura de Capas y Arquitectura

```
src/main/java/com/proyecto/servicios/
├── client/
│   ├── GestoPagoAuthClient.java       # Cliente Feign para autenticación
│   └── ProductosClient.java           # Cliente Feign para obtención de productos
├── config/
│   ├── ConfigDB.java                  # Configuración de base de datos
│   ├── FlywayConfig.java              # Configuración de migraciones DB
│   ├── OpenApi.java                   # Configuración OpenAPI / Swagger
│   └── ProductosFeignConfig.java      # Configuración de timeouts de Feign
├── controller/
│   └── ProductosController.java       # Endpoint REST GET /productos
├── entity/gestopago/
│   └── GestoPagoToken.java            # Entidad JPA para tokens
├── enums/
│   └── ErrorCode.java                 # Enum estandarizado de errores
├── exception/
│   ├── IntegrationException.java      # Excepción personalizada de integración
│   └── GlobalExceptionHandler.java    # Controlador global de excepciones @RestControllerAdvice
├── model/
│   ├── GenericResponse.java           # Envoltorio genérico para respuestas API
│   ├── gestopago/
│   │   └── GestoPagoAuthResponse.java # DTO respuesta de autenticación
│   └── productos/
│       ├── MensajeXml.java            # Jackson XML element <MENSAJE>
│       ├── ProductoXml.java           # Jackson XML element <producto>
│       ├── ProductosXml.java          # Jackson XML container <PRODUCTOS>
│       ├── ProductosXmlResponse.java  # Jackson XML root <RESPONSE>
│       └── ProductoResponse.java      # DTO interno expuesto por la API
├── repositorys/gestopago/
│   └── GestoPagoTokenRepository.java  # Repositorio Spring Data JPA
└── service/
    ├── GestoPagoTokenService.java     # Interfaz gestión de tokens
    ├── ProductosService.java          # Interfaz gestión de productos
    └── Impl/
        ├── GestoPagoTokenServiceImpl.java # Implementación servicio tokens
        └── ProductosServiceImpl.java     # Implementación servicio productos
```

---

## 5. Transformaciones de Modelos (XML a DTO Interno)

1. **Modelos Externos XML**: Jackson XML deserializa la respuesta del proveedor en la jerarquía:
   - `<RESPONSE>` -> `ProductosXmlResponse`
   - `<MENSAJE>` -> `MensajeXml`
   - `<PRODUCTOS><producto ... /></PRODUCTOS>` -> `ProductosXml` y `ProductoXml`.
2. **Modelo Interno JSON**: `ProductosServiceImpl` transforma cada `ProductoXml` a `ProductoResponse`, omitiendo envoltorios externos como `<RESPONSE>`, `<MENSAJE>`, `<CODIGO>` o `<TEXTO>` del proveedor.

---

## 6. Respuestas y Códigos de Error (`ErrorCode`)

### Respuesta Exitosa (`codigo: 0`)
La API interna responde **exclusivamente con HTTP 200 y código 0**:
```json
{
  "codigo": 0,
  "mensaje": "Productos obtenidos correctamente",
  "data": [
    {
      "servicio": "Recarga",
      "producto": "Telcel $100",
      "idServicio": "1",
      "idProducto": "100",
      "idCatTipoServicio": "2",
      "tipoFront": "S",
      "hasDigitoVerificador": false,
      "precio": "100.0",
      "showAyuda": false,
      "tipoReferencia": "NUMERO",
      "legend": "Ingresa tu número"
    }
  ]
}
```

### Tabla de Códigos `ErrorCode`
| Código | Name | Descripción | HTTP Status MAPEADO |
|---|---|---|---|
| **0** | `SUCCESS` | Operación exitosa (reservado solo para éxito) | 200 OK |
| **1** | `AUTHENTICATION_ERROR` | Error de autenticación (HTTP 401, 403 o token nulo/inválido) | 401 UNAUTHORIZED |
| **2** | `HTTP_ERROR` | Respuesta no exitosa del servicio externo (ej. HTTP 500) | 502 BAD GATEWAY |
| **3** | `TIMEOUT_ERROR` | Tiempo de espera agotado (Timeout en conexión o lectura) | 504 GATEWAY TIMEOUT |
| **4** | `COMMUNICATION_ERROR` | Error de comunicación o red | 502 BAD GATEWAY |
| **5** | `INVALID_RESPONSE` | Respuesta nula, incompleta o sin mensaje válido | 502 BAD GATEWAY |
| **6** | `CONFIGURATION_ERROR` | Error de configuración o credenciales faltantes | 500 INTERNAL SERVER ERROR |
| **7** | `INTERNAL_ERROR` | Error interno del sistema | 500 INTERNAL SERVER ERROR |

---

## 7. Estrategia de Logs y Seguridad

### Información NO registrada en logs (Protección de Datos Sensibles)
- Passwords de usuario/sistema.
- Tokens JWT completos.
- Cabeceras de autorización `Authorization: Bearer ...`.
- Cuerpos completos de respuestas sensibles.

### Información SI registrada en logs
- Inicio y fin de la consulta de productos.
- Cantidad total de productos obtenidos.
- Status HTTP en caso de errores de comunicación o integración.
- Clasificación limpia del error sin exponer datos confidenciales.

---

## 8. Cobertura de Pruebas Unitarias

Se implementó la suite de pruebas `ProductosServiceImplTest` en `src/test/java/com/proyecto/servicios/service/ProductosServiceImplTest.java` utilizando `@ExtendWith(MockitoExtension.class)` y JUnit 5, cubriendo 10 escenarios obligatorios:

1. **Consulta exitosa con token existente**: Verifica deserialización, transformación y envío de header `Bearer token-prueba`.
2. **Renovación cuando no existe token**: Verifica `verify(tokenService).renovarToken()` y re-consulta exitosa del token.
3. **Error de autenticación HTTP 401**: Verifica lanzamiento de `IntegrationException` con `ErrorCode.AUTHENTICATION_ERROR`.
4. **Error de autenticación HTTP 403**: Verifica `AUTHENTICATION_ERROR`.
5. **Error HTTP 500**: Verifica `ErrorCode.HTTP_ERROR`.
6. **Timeout**: Simulación con `FeignException.GatewayTimeout` y `SocketTimeoutException` validando `ErrorCode.TIMEOUT_ERROR`.
7. **Respuesta null**: Verifica `ErrorCode.INVALID_RESPONSE`.
8. **Respuesta sin mensaje**: Verifica `ErrorCode.INVALID_RESPONSE`.
9. **Token vacío o no disponible**: Verifica `AUTHENTICATION_ERROR` y `verifyNoInteractions(productosClient)`.
10. **Validación de DTO `ProductoResponse`**: Comprobación exhaustiva de `equals`, `hashCode`, `toString`, `assertAll`, `assertTrue` y `assertFalse`.

---

## 9. Comandos de Verificación

Para ejecutar la compilación, pruebas unitarias y arranque del proyecto:

```powershell
# 1. Limpieza y compilación Java
.\gradlew.bat clean compileJava

# 2. Ejecución de suite completa de pruebas unitarias
.\gradlew.bat clean test

# 3. Iniciar la aplicación Spring Boot
.\gradlew.bat bootRun
```
