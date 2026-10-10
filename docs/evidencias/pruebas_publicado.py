"""
Pruebas funcionales ligeras contra la API publicada (o local). NO es una prueba de carga.

Uso:
    python -X utf8 pruebas_publicado.py https://pago-servicios-api.onrender.com
    python -X utf8 pruebas_publicado.py http://localhost:8081

- Despierta el servicio si está dormido (plan gratis de Render: puede tardar ~1 minuto).
- Crea UN cliente de prueba (ocupación "Prueba automatizada") y al final lo da de baja (baja lógica),
  identificándolo por su CURP: la API ya no usa ni devuelve el ID del cliente.
- Detecta si la versión publicada ya incluye la consulta (bandera 4).
Solo usa la librería estándar de Python 3.10+.
"""
import json
import random
import ssl
import string
import sys
import time
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8081").rstrip("/")
CLI, LOGIN, CAT = "/api/v1/layaway/cliente", "/api/v1/auth/login", "/api/v1/cat/nacionalidades"
resultados = []


def llamar(metodo, ruta, cuerpo=None, timeout=120):
    datos = cuerpo.encode("utf-8") if isinstance(cuerpo, str) else (json.dumps(cuerpo).encode("utf-8") if cuerpo is not None else None)
    req = urllib.request.Request(BASE + ruta, data=datos, method=metodo,
                                 headers={"Content-Type": "application/json", "Accept": "application/json"})
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ssl.create_default_context()) as r:
            texto, estado = r.read().decode("utf-8", "replace"), r.status
    except urllib.error.HTTPError as e:
        texto, estado = e.read().decode("utf-8", "replace"), e.code
    ms = int((time.time() - t0) * 1000)
    try:
        return estado, json.loads(texto), ms
    except ValueError:
        return estado, texto, ms


def check(nombre, esperado, estado, cuerpo, ms, condicion=None):
    ok = estado in (esperado if isinstance(esperado, tuple) else (esperado,)) and (condicion(cuerpo) if condicion else True)
    resultados.append(ok)
    msg = cuerpo.get("mensaje") if isinstance(cuerpo, dict) else str(cuerpo)[:80]
    print(f"{'PASS' if ok else 'FAIL'} [{estado} esp {esperado}] {ms:>5} ms  {nombre} :: {str(msg)[:110]}")
    return ok


def consonantes(n):
    return "".join(random.choice("BCDFGHJKLMNPQRSTVWXZ") for _ in range(n))


def cliente():
    letras = consonantes(4)
    curp = f"{letras}900310HGT{consonantes(3)}0{random.randint(0, 9)}"
    rfc = f"{letras}900310{consonantes(2)}{random.randint(0, 9)}"
    sufijo = "".join(random.choice(string.ascii_lowercase) for _ in range(8))
    cuerpo = ('{"bandera":1,"datosPersonales":{"nombre":"Prueba","apellidoPaterno":"Automatica","apellidoMaterno":"Render",'
              '"fechaNacimiento":"1990-03-10","curp":"%s","rfc":"%s","sexo":"MASCULINO","nacionalidad":"MEXICANA","estadoCivil":"SOLTERO"},'
              '"datosContacto":{"correo":"prueba.%s@example.com","telefonoMovil":4181234567},'
              '"domicilio":{"calle":"Hidalgo","numeroExterior":"1","colonia":"Centro","municipio":"Dolores Hidalgo","estado":"Guanajuato","codigoPostal":"37800","pais":"MEXICO"},'
              '"informacionLaboral":{"ocupacion":"Prueba automatizada","empresa":"Pruebas","ingresoMensual":15000.00},'
              '"loginCredenciales":{"username":"prueba.%s","password":"Password123!"}}') % (curp, rfc, sufijo, sufijo)
    return cuerpo, curp, rfc, f"prueba.{sufijo}"


print(f"Objetivo: {BASE}\n")
print("--- Disponibilidad (si el servicio estaba dormido, la primera respuesta tarda) ---")
for intento in range(1, 4):
    try:
        estado, cuerpo, ms = llamar("GET", "/api-docs", timeout=150)
        break
    except Exception as e:  # servicio arrancando, corte de conexión, etc.
        print(f"intento {intento}: {type(e).__name__}: {e}")
        time.sleep(15)
else:
    print("No se pudo conectar con el servicio. Revise la URL y que Render esté en 'Live'.")
    sys.exit(1)
check("GET /api-docs (documentación OpenAPI)", 200, estado, cuerpo, ms)
estado, cuerpo, ms = llamar("GET", "/swagger-ui.html")
check("GET /swagger-ui.html", 200, estado, "(página HTML)", ms)

print("\n--- Base de datos (Neon) y catálogo ---")
estado, cuerpo, ms = llamar("GET", CAT)
check("GET catálogo de nacionalidades", 200, estado, cuerpo, ms,
      lambda c: any(n["nombre"] == "MEXICANA" for n in c["data"]))

print("\n--- Registro ---")
alta, curp, rfc, usuario = cliente()
estado, cuerpo, ms = llamar("POST", CLI, alta)
creado = check("Alta de cliente válida", 201, estado, cuerpo, ms,
               lambda c: len(c["data"]["numeroCuenta"]) == 12 and c["data"]["estatusCuenta"] == "ACTIVA" and c["data"]["saldoInicial"] == 1000
               and "clienteId" not in c["data"])
estado, cuerpo, ms = llamar("POST", CLI, alta)
check("Alta duplicada (misma CURP)", 409, estado, cuerpo, ms)
estado, cuerpo, ms = llamar("POST", CLI, alta.replace('"nombre":"Prueba"', '"nombre":"Li"').replace('"MASCULINO"', '"masculino"')
                            .replace(f'"{curp}"', f'"{curp[:6]}13{curp[8:]}"'))
check("Datos inválidos (nombre corto, sexo en minúsculas, CURP con mes 13)", 400, estado, cuerpo, ms,
      lambda c: "sexo" in c["mensaje"] and "nombre" in c["mensaje"])
estado, cuerpo, ms = llamar("POST", CLI, '{"bandera":"1"}')
check("Bandera como texto", 400, estado, cuerpo, ms)

estado, cuerpo, ms = llamar("POST", CLI, alta.replace('"Password123!"', '"password123"').replace(usuario, usuario + "x").replace(curp, curp[:-2] + "99").replace(rfc, rfc[:-1] + "9"))
check("Contraseña débil (sin mayúscula ni carácter especial)", 400, estado, cuerpo, ms, lambda c: "contraseña" in c["mensaje"])
estado, cuerpo, ms = llamar("POST", CLI, {"bandera": 2, "filtros": {"curp": curp}, "datosContacto": {"correo": "nuevo.%s@example.com" % usuario, "telefonoMovil": 4189998877}})
check("Actualizar por CURP (sin ID)", 200, estado, cuerpo, ms, lambda c: "clienteId" not in c["data"])
estado, cuerpo, ms = llamar("POST", CLI, {"bandera": 2, "filtros": {"curp": curp[:6]}})
check("Actualizar con llave incompleta", 400, estado, cuerpo, ms)
estado, cuerpo, ms = llamar("POST", CLI, {"bandera": 2, "clienteId": 1})
check("Actualizar con clienteId (ya no se usa)", 400, estado, cuerpo, ms)

print("\n--- Login ---")
estado, cuerpo, ms = llamar("POST", LOGIN, {"bandera": 1, "username": usuario, "password": "Password123!"})
check("Login correcto", 200, estado, cuerpo, ms, lambda c: c["data"]["isLoggedIn"] is True)
estado, cuerpo, ms = llamar("POST", LOGIN, {"bandera": 1, "username": usuario, "password": "Incorrecta123"})
check("Login con contraseña incorrecta", 401, estado, cuerpo, ms)
estado, cuerpo, ms = llamar("POST", LOGIN, {"bandera": 1, "username": "usuario.que.no.existe", "password": "Incorrecta123"})
check("Login con usuario inexistente (mismo 401)", 401, estado, cuerpo, ms)

print("\n--- Consulta (bandera 4) ---")
estado, cuerpo, ms = llamar("POST", CLI, {"bandera": 4, "filtros": {"curp": curp[:8]}})
if estado == 400 and isinstance(cuerpo, dict) and "no válida" in cuerpo.get("mensaje", ""):
    print("OMITIDO  La versión publicada todavía NO incluye la consulta (bandera 4): falta hacer push y redesplegar.")
else:
    check("Consulta por parte de la CURP", 200, estado, cuerpo, ms,
          lambda c: any(k["curp"] == curp for k in c["data"]["clientes"]))
    estado, cuerpo, ms = llamar("POST", CLI, {"bandera": 4})
    check("Consulta sin filtros (todos los clientes)", 200, estado, cuerpo, ms, lambda c: c["data"]["totalCoincidencias"] >= 1)
    estado, cuerpo, ms = llamar("POST", CLI, '{"bandera":4,"filtros":{"curp":123}}')
    check("Filtro sin comillas", 400, estado, cuerpo, ms)
    estado, cuerpo, ms = llamar("POST", CLI, {"bandera": 4, "filtros": {"curp": ""}})
    check("Filtro en blanco", 400, estado, cuerpo, ms)
    estado, cuerpo, ms = llamar("POST", CLI, {"bandera": 4, "filtros": {"nombre": "f"}})
    check("Filtro no permitido (nombre)", 400, estado, cuerpo, ms)

print("\n--- Limpieza: baja lógica del cliente de prueba ---")
if creado:
    estado, cuerpo, ms = llamar("POST", CLI, {"bandera": 3, "filtros": {"curp": curp}})
    check("Baja lógica del cliente de prueba (por CURP)", 200, estado, cuerpo, ms, lambda c: c["data"]["activo"] is False)
else:
    print("No se creó cliente de prueba; no hay nada que limpiar.")

print(f"\nTOTAL {len(resultados)}  PASS {sum(resultados)}  FAIL {len(resultados) - sum(resultados)}")
sys.exit(0 if all(resultados) else 1)
