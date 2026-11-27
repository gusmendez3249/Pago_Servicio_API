# Gesto Pago - App móvil (`gesto_pago_mobile`)

App Flutter (Material 3, Riverpod, go_router, Dio) de la Plataforma de Pagos de Servicios.
Consume el backend propio (Spring Boot), nunca Gesto Pago directamente.

Estado: Sprint 3 (login, catálogo, validación de referencia y pago: resumen, confirmación con PIN, resultado y
comprobante). Las carpetas de plataforma (`android/`, `web/`) ya están generadas y versionadas; no hace falta
volver a ejecutar `flutter create`.

## Requisitos

Flutter estable (verificado con Flutter 3.50 / Dart 3.14) y el backend en `http://localhost:8081`.
No se añaden paquetes fuera de los de `pubspec.yaml`.

```bash
cd mobile
flutter pub get
```

## Ejecutar (la URL del backend es obligatoria)

En Chrome (el backend debe permitir el origen de la web en CORS):

```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8081
```

En el emulador de Android (`10.0.2.2` es el localhost de la máquina anfitriona):

```bash
flutter run -d <id-del-emulador> --dart-define=API_BASE_URL=http://10.0.2.2:8081
```

Sin `API_BASE_URL` la app muestra una pantalla de error de configuración.
Alternativa con archivo local (ignorado por git): crea `env.json` con
`{"API_BASE_URL": "http://..."}` y ejecuta `flutter run --dart-define-from-file=env.json`.

Compilación de comprobación para web:

```bash
flutter build web --dart-define=API_BASE_URL=http://localhost:8081
```

## Crear cuenta

Desde el inicio de sesión, "Crear cuenta" abre el registro en 4 pasos (`/registro`, `lib/features/registro`): tus datos,
contacto y domicilio, trabajo y acceso. Las reglas (dígito de la CURP, CURP congruente con fecha y sexo, RFC, mayoría de
edad, contraseña y PIN) son las mismas del backend y de la web. La cuenta nace con saldo $0.00; el saldo solo lo abona
un administrador desde el panel de la web (`/admin`).

## Configuración de plataforma

- Android: `INTERNET` está en el manifiesto principal y `minSdk` es 23 (requisito de `flutter_secure_storage`).
  El tráfico en claro (`http://`) solo se permite en el manifiesto de debug
  (`android/app/src/debug/AndroidManifest.xml`); en release usa https.
- La versión release exige `API_BASE_URL` con `https://` (solo se tolera `http` hacia `localhost`, `127.0.0.1` o
  `10.0.2.2`); con otra URL la app muestra la pantalla de error de configuración.
- Firma de release: crea `android/key.properties` (ignorado por git) con `storeFile`, `storePassword`, `keyAlias` y
  `keyPassword`. Sin ese archivo la release queda sin firmar; nunca se firma con la llave de depuración.
  ```bash
  keytool -genkey -v -keystore ~/bangus-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias bangus
  ```
- Fuentes: Libre Franklin (400 a 700) y Source Serif 4 (600) vienen en `assets/fonts/` (licencia OFL, junto a los
  archivos). `main.dart` apaga `GoogleFonts.config.allowRuntimeFetching`: la app no contacta a Google y funciona sin
  conexión. Si el tema usa un peso nuevo, agrega su TTF con el nombre `Familia-Peso.ttf` (p. ej. `LibreFranklin-Light.ttf`).
- Los logotipos se cargan con `Image.network` desde URLs absolutas que entrega el backend.
- iOS no está generado; si se necesita, `flutter create . --platforms=ios` y, para backends `http://`,
  una excepción de App Transport Security solo en desarrollo.

## Pruebas y análisis

```bash
flutter analyze
flutter test
```

## Estructura

```
lib/core/       config, http (ApiClient, Result), interceptors, router, error, session
lib/shared/     widgets reutilizables
lib/features/   auth, catalogo, referencia, pago, historial (data / domain / presentation)
```

Sesión: el token (opaco) vence tras 5 min sin actividad; se guarda solo en
`flutter_secure_storage`. No hay refresh token.
