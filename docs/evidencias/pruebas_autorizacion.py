"""
Pruebas de autenticación y autorización a nivel de recurso contra la API en ejecución.

Comprueba que:
  * las operaciones protegidas (banderas 2, 3 y 4) exigen un token válido;
  * un CLIENTE solo ve y modifica SU propio registro, aunque envíe las llaves de otro cliente;
  * solo un EJECUTIVO puede dar de baja y operar sobre cualquier cliente;
  * la baja y el cambio de contraseña invalidan la sesión;
  * las respuestas no exponen el ID interno y los errores comparten un mismo formato.

Uso (la API debe arrancar con APP_EJECUTIVO_USUARIO y APP_EJECUTIVO_PASSWORD definidos):
    set EJECUTIVO_USUARIO=ejecutivo.admin
    set EJECUTIVO_PASSWORD=Ejecutivo#2026
    python -X utf8 pruebas_autorizacion.py http://localhost:8081

ADVERTENCIA: inserta clientes de prueba. Ejecutar contra una base de datos de pruebas.
Solo usa la librería estándar de Python 3.10+.
"""
import json
import os
import random
import string
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8081").rstrip("/")
CLI, LOGIN = "/api/v1/layaway/cliente", "/api/v1/auth/login"
USUARIO_EJ = os.environ.get("EJECUTIVO_USUARIO", "ejecutivo.admin")
PASSWORD_EJ = os.environ.get("EJECUTIVO_PASSWORD", "Ejecutivo#2026")
resultados = []


def llamar(ruta, cuerpo=None, token=None, encabezados=None, metodo="POST"):
    datos = (cuerpo if isinstance(cuerpo, str) else json.dumps(cuerpo)).encode("utf-8") if cuerpo is not None else None
    h = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    h.update(encabezados or {})
    req = urllib.request.Request(BASE + ruta, data=datos, method=metodo, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            texto, estado, cab = r.read().decode("utf-8", "replace"), r.status, dict(r.headers)
    except urllib.error.HTTPError as e:
        texto, estado, cab = e.read().decode("utf-8", "replace"), e.code, dict(e.headers)
    try:
        return estado, json.loads(texto), cab
    except ValueError:
        return estado, texto, cab


def check(nombre, esperado, estado, cuerpo, condicion=None):
    ok = estado == esperado and (condicion(cuerpo) if condicion else True)
    resultados.append(ok)
    msg = cuerpo.get("mensaje") if isinstance(cuerpo, dict) else str(cuerpo)[:80]
    print(f"{'PASS' if ok else 'FAIL'} [{estado} esp {esperado}] {nombre} :: {str(msg)[:120]}")
    return ok


def consonantes(n):
    return "".join(random.choice("BCDFGHJKLMNPQRSTVWXZ") for _ in range(n))


def nuevo_cliente(nombre):
    letras = consonantes(4)
    curp = f"{letras}900310HGT{consonantes(3)}0{random.randint(0, 9)}"
    rfc = f"{letras}900310{consonantes(2)}{random.randint(0, 9)}"
    sufijo = "".join(random.choice(string.ascii_lowercase) for _ in range(8))
    usuario = f"{nombre.lower()}.{sufijo}"
    cuerpo = ('{"bandera":1,"datosPersonales":{"nombre":"%s","apellidoPaterno":"Autorizacion","apellidoMaterno":"Prueba",'
              '"fechaNacimiento":"1990-03-10","curp":"%s","rfc":"%s","sexo":"MASCULINO","nacionalidad":"MEXICANA","estadoCivil":"SOLTERO"},'
              '"datosContacto":{"correo":"%s@example.com","telefonoMovil":4181234567},'
              '"domicilio":{"calle":"Hidalgo","numeroExterior":"1","colonia":"Centro","municipio":"DH","estado":"Guanajuato","codigoPostal":"37800","pais":"MEXICO"},'
              '"informacionLaboral":{"ocupacion":"Prueba autorizacion","empresa":"X","ingresoMensual":15000.00},'
              '"loginCredenciales":{"username":"%s","password":"Password123!"}}') % (nombre, curp, rfc, usuario, usuario)
    estado, j, _ = llamar(CLI, cuerpo)
    assert estado == 201, (estado, j)
    return {"nombre": nombre, "curp": curp, "rfc": rfc, "cuenta": j["data"]["numeroCuenta"], "usuario": usuario,
            "correo": usuario + "@example.com"}


def entrar(usuario, password):
    estado, j, _ = llamar(LOGIN, {"bandera": 1, "username": usuario, "password": password})
    return estado, j


def clave(**llaves):
    return {"filtros": llaves}


def sin_id(j):
    return isinstance(j, dict) and "clienteId" not in json.dumps(j)


print(f"Objetivo: {BASE}\n")
print("===== EJECUTIVO INICIAL Y REGISTRO PUBLICO =====")
estado, j = entrar(USUARIO_EJ, PASSWORD_EJ)
check("login del ejecutivo", 200, estado, j, lambda c: c["data"]["rol"] == "EJECUTIVO" and len(c["data"]["token"]) == 43)
if estado != 200:
    print("No se pudo iniciar sesión como ejecutivo: arranque la API con APP_EJECUTIVO_USUARIO / APP_EJECUTIVO_PASSWORD.")
    sys.exit(1)
T_EJ = j["data"]["token"]
check("la respuesta del login no expone el ID", 200, estado, j, sin_id)
A, B = nuevo_cliente("Alicia"), nuevo_cliente("Beto")
print(f"alta pública (sin token) de dos clientes: {A['usuario']} y {B['usuario']}")

print("\n===== SIN TOKEN / TOKEN INVALIDO =====")
for bandera, cuerpo in [(2, {"bandera": 2, **clave(curp=A["curp"]), "datosContacto": {"correo": A["correo"], "telefonoMovil": 4189998877}}),
                        (3, {"bandera": 3, **clave(curp=A["curp"])}), (4, {"bandera": 4})]:
    estado, j, cab = llamar(CLI, cuerpo)
    check(f"bandera {bandera} sin encabezado Authorization", 401, estado, j,
          lambda c: "autenticación" in c["mensaje"] and c["codigo"] == 401)
    check(f"bandera {bandera} -> trae WWW-Authenticate: Bearer", 401, estado, j,
          lambda c, cab=cab: "Bearer" in cab.get("WWW-Authenticate", ""))
for nombre, token in [("token inventado", "x" * 43), ("token corto", "abc"), ("token con comillas", "' OR '1'='1' --aaaaaaaaaaaaaaaa"),
                      ("token con signos no permitidos", "<script>alert(1)</script>aaaaaaaaaa")]:
    estado, j, _ = llamar(CLI, {"bandera": 4}, token=token)
    check(f"bandera 4 con {nombre}", 401, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 4}, encabezados={"Authorization": "Basic dXNlcjpwYXNz"})
check("bandera 4 con esquema Basic", 401, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 4}, encabezados={"Authorization": "Bearer"})
check("bandera 4 con 'Bearer' sin token", 401, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 5})
check("bandera inválida se rechaza antes de autenticar (400)", 400, estado, j)

print("\n===== CLIENTE: SOLO SU PROPIO REGISTRO =====")
estado, ja = entrar(A["usuario"], "Password123!")
check("login de Alicia (rol CLIENTE)", 200, estado, ja, lambda c: c["data"]["rol"] == "CLIENTE")
estado, jb = entrar(B["usuario"], "Password123!")
T_A, T_B = ja["data"]["token"], jb["data"]["token"]

estado, j, _ = llamar(CLI, {"bandera": 4}, token=T_A)
check("Alicia consulta SIN filtros: solo ve su registro (no todos)", 200, estado, j,
      lambda c: [k["curp"] for k in c["data"]["clientes"]] == [A["curp"]] and c["data"]["totalCoincidencias"] == 1)
check("  y la respuesta no expone el ID", 200, estado, j, sin_id)
estado, j, _ = llamar(CLI, {"bandera": 4, **clave(curp=A["curp"])}, token=T_A)
check("Alicia consulta su propia CURP", 200, estado, j, lambda c: len(c["data"]["clientes"]) == 1)
estado, j, _ = llamar(CLI, {"bandera": 4, **clave(curp=B["curp"])}, token=T_A)
check("Alicia consulta la CURP de Beto -> 404 (no revela si existe)", 404, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 4, **clave(rfc=B["rfc"])}, token=T_A)
check("Alicia consulta el RFC de Beto -> 404", 404, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 4, **clave(numeroCuenta=B["cuenta"])}, token=T_A)
check("Alicia consulta la cuenta de Beto -> 404", 404, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 4, **clave(curp=B["curp"][:4])}, token=T_A)
check("Alicia busca por un fragmento de la CURP de Beto -> 404", 404, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 4, **clave(curp="")}, token=T_A)
check("filtro en blanco sigue siendo 400", 400, estado, j)
inexistente_a = llamar(CLI, {"bandera": 4, **clave(curp="ZZZZ900101HDFRMN01")}, token=T_A)
inexistente_ej = llamar(CLI, {"bandera": 4, **clave(curp="ZZZZ900101HDFRMN01")}, token=T_EJ)
check("CURP que no existe -> 404 (ejecutivo)", 404, inexistente_ej[0], inexistente_ej[1])
check("la respuesta a 'no existe' y a 'es de otro' es la misma (sin enumeración)", 404, inexistente_a[0], inexistente_a[1],
      lambda c: c["mensaje"] == llamar(CLI, {"bandera": 4, **clave(curp=B["curp"])}, token=T_A)[1]["mensaje"])

estado, j, _ = llamar(CLI, {"bandera": 2, **clave(curp=B["curp"]), "datosContacto": {"correo": B["correo"], "telefonoMovil": 4189998877}}, token=T_A)
check("Alicia intenta ACTUALIZAR a Beto -> 404", 404, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 2, **clave(curp=B["curp"]), "loginCredenciales": {"username": B["usuario"], "password": "Hackeado#2026"}}, token=T_A)
check("Alicia intenta CAMBIAR LA CONTRASENA de Beto -> 404", 404, estado, j)
estado, j = entrar(B["usuario"], "Password123!")
check("Beto sigue entrando con su contrasena original", 200, estado, j)
T_B = j["data"]["token"]
estado, j, _ = llamar(CLI, {"bandera": 4}, token=T_EJ)
check("el ejecutivo comprueba que el teléfono de Beto NO cambió", 200, estado, j,
      lambda c: next(k for k in c["data"]["clientes"] if k["curp"] == B["curp"])["telefonoMovil"] == "4181234567")
estado, j, _ = llamar(CLI, {"bandera": 2, **clave(curp=A["curp"]), "datosContacto": {"correo": A["correo"], "telefonoMovil": 4187776655}}, token=T_A)
check("Alicia actualiza SU registro", 200, estado, j, sin_id)
estado, j, _ = llamar(CLI, {"bandera": 3, **clave(curp=A["curp"])}, token=T_A)
check("Alicia intenta darse de baja -> 403 (solo ejecutivos)", 403, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 3, **clave(curp=B["curp"])}, token=T_A)
check("Alicia intenta dar de baja a Beto -> 403", 403, estado, j)

print("\n===== CAMBIO DE CONTRASENA CIERRA LA SESION =====")
estado, j, _ = llamar(CLI, {"bandera": 2, **clave(curp=A["curp"]), "loginCredenciales": {"username": A["usuario"], "password": "debil"}}, token=T_A)
check("contraseña débil -> 400 con detalle por campo", 400, estado, j,
      lambda c: c["codigo"] == 400 and any(e["campo"] == "loginCredenciales.password" for e in c["data"]))
estado, j, _ = llamar(CLI, {"bandera": 2, **clave(curp=A["curp"]), "loginCredenciales": {"username": A["usuario"], "password": "NuevaClave#2026"}}, token=T_A)
check("Alicia cambia su contraseña", 200, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 4}, token=T_A)
check("el token anterior de Alicia ya no sirve -> 401", 401, estado, j)
estado, j = entrar(A["usuario"], "NuevaClave#2026")
check("Alicia entra con la contraseña nueva", 200, estado, j)
T_A = j["data"]["token"]
estado, j, _ = llamar(LOGIN, {"bandera": 1, "username": A["usuario"], "password": "Password123!"})
check("la contraseña vieja ya no sirve", 401, estado, j)
estado, j, _ = llamar(CLI, {"bandera": 4}, token=T_A)
check("y el token anterior (previo al nuevo login) ya no sirve; el nuevo sí", 200, estado, j)

print("\n===== EJECUTIVO: TODOS LOS REGISTROS =====")
estado, j, _ = llamar(CLI, {"bandera": 4}, token=T_EJ)
curps = [k["curp"] for k in j["data"]["clientes"]] if estado == 200 else []
check("el ejecutivo consulta sin filtros y ve a todos", 200, estado, j, lambda c: A["curp"] in curps and B["curp"] in curps)
estado, j, _ = llamar(CLI, {"bandera": 4, **clave(curp=B["curp"])}, token=T_EJ)
check("el ejecutivo consulta a Beto", 200, estado, j, lambda c: len(c["data"]["clientes"]) == 1)
estado, j, _ = llamar(CLI, {"bandera": 2, **clave(curp=B["curp"]), "datosContacto": {"correo": B["correo"], "telefonoMovil": 4180001122}}, token=T_EJ)
check("el ejecutivo actualiza a Beto", 200, estado, j, sin_id)
estado, j, _ = llamar(CLI, {"bandera": 3, **clave(curp=B["curp"])}, token=T_EJ)
check("el ejecutivo da de baja a Beto", 200, estado, j, lambda c: c["data"]["activo"] is False and sin_id(c))
estado, j, _ = llamar(CLI, {"bandera": 4}, token=T_B)
check("el token de Beto deja de servir tras la baja -> 401", 401, estado, j)
estado, j = entrar(B["usuario"], "Password123!")
check("Beto ya no puede iniciar sesión (cliente inactivo) -> 403", 403, estado, j)

print("\n===== ERRORES CON FORMATO CONSISTENTE =====")
estado, j, _ = llamar(CLI, {"bandera": 1, "datosPersonales": {"nombre": "Li"}})
check("validación: codigo = 400 y detalle por campo en data", 400, estado, j,
      lambda c: c["codigo"] == 400 and isinstance(c["data"], list) and {"campo", "mensaje"} <= set(c["data"][0]))
estado, j, _ = llamar(CLI, "{")
check("JSON mal formado: codigo = 400", 400, estado, j, lambda c: c["codigo"] == 400)
estado, j, _ = llamar(CLI, {"bandera": 4}, encabezados={"Content-Type": "text/plain"}, token=T_EJ)
check("Content-Type incorrecto: codigo = 415", 415, estado, j, lambda c: c["codigo"] == 415)
estado, j, _ = llamar(CLI, {"bandera": 4}, token=T_EJ, metodo="GET")
check("método no permitido: codigo = 405", 405, estado, j, lambda c: c["codigo"] == 405)
estado, j, _ = llamar("/api/v1/no-existe", metodo="GET")
check("recurso inexistente: codigo = 404", 404, estado, j, lambda c: c["codigo"] == 404)
for ruta in (CLI, LOGIN):
    estado, j, _ = llamar(ruta, {"bandera": 1})
    check(f"{ruta}: todo error trae codigo, mensaje y data", estado, estado, j, lambda c: {"codigo", "mensaje", "data"} <= set(c))

print(f"\nTOTAL {len(resultados)}  PASS {sum(resultados)}  FAIL {len(resultados) - sum(resultados)}")
sys.exit(0 if all(resultados) else 1)
