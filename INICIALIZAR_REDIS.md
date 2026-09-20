# Proyecto de Servicios - Integración y Caché con Redis

Este proyecto es una aplicación **Spring Boot** para la gestión e integración de productos de pago de servicios, utilizando **Redis** como capa de caché de alto rendimiento y **PostgreSQL** para la persistencia de datos.

---

## 🚀 Gestión del Servidor Redis (Windows)

La instancia local de Redis se instaló a través de Winget (`taizod1024.redis-windows-fork`). A continuación se detallan las instrucciones para verificar, iniciar y detener el servidor de Redis.

### 📍 Ruta del Ejecutable
```text
C:\Users\gusta\AppData\Local\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork_Microsoft.Winget.Source_8wekyb3d8bbwe\Redis-8.10.1-Windows-x64-msys2\redis-server.exe
```

---

### 1. 🟢 Cómo Iniciar Redis

#### Opción A: En segundo plano (Recomendado)
Abre PowerShell y ejecuta:
```powershell
Start-Process -FilePath "C:\Users\gusta\AppData\Local\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork_Microsoft.Winget.Source_8wekyb3d8bbwe\Redis-8.10.1-Windows-x64-msys2\redis-server.exe" -WindowStyle Hidden
```

#### Opción B: En primer plano (Visualizando logs en consola)
```powershell
& "C:\Users\gusta\AppData\Local\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork_Microsoft.Winget.Source_8wekyb3d8bbwe\Redis-8.10.1-Windows-x64-msys2\redis-server.exe"
```

---

### 2. 🔍 Cómo Verificar el Estado de Redis

#### Verificar conexión TCP al puerto 6379:
```powershell
Test-NetConnection -ComputerName localhost -Port 6379
```
*(Debe responder `TcpTestSucceeded : True`)*

#### Probar ping con `redis-cli`:
```powershell
& "C:\Users\gusta\AppData\Local\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork_Microsoft.Winget.Source_8wekyb3d8bbwe\Redis-8.10.1-Windows-x64-msys2\redis-cli.exe" ping
```
*(Debe responder `PONG`)*

---

### 3. 🔴 Cómo Detener Redis

#### Opción A: Apagado limpio vía `redis-cli` (Recomendado)
Envía la orden de shutdown al servidor Redis para guardar en disco antes de cerrar:
```powershell
& "C:\Users\gusta\AppData\Local\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork_Microsoft.Winget.Source_8wekyb3d8bbwe\Redis-8.10.1-Windows-x64-msys2\redis-cli.exe" shutdown
```

#### Opción B: Detención del proceso desde PowerShell
```powershell
Stop-Process -Name "redis-server" -Force
```

#### Opción C: Usando Command Prompt / PowerShell con `taskkill`
```cmd
taskkill /F /IM redis-server.exe
```

---

## 🛠️ Comandos de Ejecución del Proyecto (Spring Boot / Gradle)

En la raíz del proyecto (`prueba/`):

```powershell
# 1. Limpieza y compilación
.\gradlew.bat clean compileJava

# 2. Ejecutar pruebas unitarias
.\gradlew.bat clean test

# 3. Iniciar la aplicación Spring Boot
.\gradlew.bat bootRun
```
