"""
Batería de validación extrema contra la API en ejecución (local o publicada). Secuencial y con pausa entre
peticiones: busca romper las validaciones, no saturar el servidor.

Cubre: JSON crudo y bandera (tipos, duplicados, anidamiento, tamaño), cabeceras y métodos (incluido XML),
cada campo del registro con valores inválidos, asignación masiva de campos del servidor, unicidad, login,
manipulación del token y, como cliente autenticado, filtros, actualización y baja. Ninguna respuesta debe ser
500 ni contener trazas, nombres de clases o SQL.

Uso:
    python -X utf8 pruebas_validacion_extrema.py https://pago-servicios-api.onrender.com
    python -X utf8 pruebas_validacion_extrema.py http://localhost:8081

ADVERTENCIA: registra algunos clientes de prueba (usuarios "val.xxxxxxxx").
"""
import copy
import re
import http.client
import json
import random
import ssl
import string
import sys
import time
from datetime import date, timedelta
from urllib.parse import urlparse

URL = urlparse(sys.argv[1].rstrip("/"))
HOST = URL.hostname
CLI, LOGIN, CAT = "/api/v1/layaway/cliente", "/api/v1/auth/login", "/api/v1/cat/nacionalidades"
PAUSA = 0.12
res, hallazgos = [], []
FUGAS = ("Exception", "java.", "org.springframework", "org.hibernate", "SQLState", "at com.", "ERROR:", "psql", "Hikari", "stacktrace", "PSQLException")


def raw(metodo, ruta, cuerpo=None, headers=None, chunked=False):
    """cuerpo: bytes|str|obj. Devuelve (estado, json|texto, cabeceras)."""
    time.sleep(PAUSA)
    if cuerpo is not None and not isinstance(cuerpo, (bytes, str)):
        cuerpo = re.sub(r'"__N:([^"]*)__"', r"\1", json.dumps(cuerpo, ensure_ascii=False))
    if isinstance(cuerpo, str):
        cuerpo = cuerpo.encode("utf-8")
    h = {"Content-Type": "application/json", "Accept": "application/json"}
    h.update(headers or {})
    h = {k: v for k, v in h.items() if v is not None}
    for intento in range(3):
        try:
            c = (http.client.HTTPSConnection(HOST, URL.port or 443, timeout=90, context=ssl.create_default_context()) if URL.scheme == "https"
                 else http.client.HTTPConnection(HOST, URL.port or 80, timeout=90))
            if chunked:
                c.putrequest(metodo, ruta)
                for k, v in h.items():
                    c.putheader(k, v)
                c.putheader("Transfer-Encoding", "chunked")
                c.endheaders()
                for i in range(0, len(cuerpo), 8192):
                    parte = cuerpo[i:i + 8192]
                    c.send(b"%x\r\n%s\r\n" % (len(parte), parte))
                c.send(b"0\r\n\r\n")
            else:
                c.request(metodo, ruta, body=cuerpo, headers=h)
            r = c.getresponse()
            texto = r.read().decode("utf-8", "replace")
            cab = dict(r.getheaders())
            c.close()
            try:
                return r.status, json.loads(texto), cab
            except ValueError:
                return r.status, texto, cab
        except (ConnectionError, http.client.HTTPException, OSError) as e:
            if intento == 2:
                return 0, f"{type(e).__name__}: {e}", {}
            time.sleep(3)


def check(nombre, esperado, r, cond=None):
    estado, cuerpo, _ = r
    esperado = esperado if isinstance(esperado, tuple) else (esperado,)
    texto = json.dumps(cuerpo, ensure_ascii=False) if not isinstance(cuerpo, str) else cuerpo
    fuga = [f for f in FUGAS if f in texto]
    ok = estado in esperado and not fuga and (cond(cuerpo) if cond else True)
    res.append(ok)
    msg = cuerpo.get("mensaje") if isinstance(cuerpo, dict) else str(cuerpo)[:90]
    print(f"{'PASS' if ok else 'FAIL'} [{estado} esp {'/'.join(map(str, esperado))}] {nombre} :: {str(msg)[:120]}"
          + (f"  <<FUGA {fuga}>>" if fuga else ""))
    if not ok:
        hallazgos.append((nombre, estado, str(msg)[:300]))
    return ok


def consonantes(n):
    return "".join(random.choice("BCDFGHJKLMNPQRSTVWXZ") for _ in range(n))


def nuevo():
    letras = consonantes(4)
    suf = "".join(random.choice(string.ascii_lowercase) for _ in range(8))
    return {
        "bandera": 1,
        "datosPersonales": {"nombre": "Prueba", "apellidoPaterno": "Validacion", "apellidoMaterno": "Render",
                            "fechaNacimiento": "1990-03-10", "curp": f"{letras}900310HGT{consonantes(3)}0{random.randint(0, 9)}",
                            "rfc": f"{letras}900310{consonantes(2)}{random.randint(0, 9)}", "sexo": "MASCULINO",
                            "nacionalidad": "MEXICANA", "estadoCivil": "SOLTERO"},
        "datosContacto": {"correo": f"val.{suf}@example.com", "telefonoMovil": 4181234567},
        "domicilio": {"calle": "Hidalgo", "numeroExterior": "1", "colonia": "Centro", "municipio": "Dolores Hidalgo",
                      "estado": "Guanajuato", "codigoPostal": "37800", "pais": "MEXICO"},
        "informacionLaboral": {"ocupacion": "Pruebas", "empresa": "QA", "ingresoMensual": "__N:15000.00__"},
        "loginCredenciales": {"username": f"val.{suf}", "password": "Password123!"},
    }


def con(base, ruta, valor):
    """Copia de base con el campo 'a.b' = valor (valor '__QUITAR__' elimina el campo)."""
    b = copy.deepcopy(base)
    partes = ruta.split(".")
    d = b
    for p in partes[:-1]:
        d = d[p]
    if valor == "__QUITAR__":
        d.pop(partes[-1], None)
    else:
        d[partes[-1]] = valor
    return b


def es_val(c):
    return isinstance(c, dict) and c.get("codigo") == 1 and isinstance(c.get("data"), list) and c["data"]


def es_400(c):
    return isinstance(c, dict) and c.get("codigo") in (1, 400)


print(f"Objetivo: https://{HOST}\n")
check("despierta el servicio (GET /api-docs)", 200, raw("GET", "/api-docs"))

# ======================================================================== 1. JSON y bandera
print("\n===== 1. JSON CRUDO Y BANDERA =====")
for nombre, cuerpo in [("cuerpo vacío", b""), ("null", "null"), ("arreglo", "[]"), ("número", "123"), ("texto", '"hola"'),
                       ("JSON roto", '{"bandera":'), ("coma final", '{"bandera":4,}'), ("comillas simples", "{'bandera':4}"),
                       ("comentario", '{/*x*/"bandera":4}'), ("NaN", '{"bandera":NaN}'), ("BOM UTF-8", b'\xef\xbb\xbf{"bandera":9}'),
                       ("UTF-16", '{"bandera":9}'.encode("utf-16")), ("bytes inválidos", b'{"bandera":9,"x":"\xff\xfe"}'),
                       ("byte nulo", b'{"bandera":9\x00}')]:
    check(f"cuerpo: {nombre}", 400, raw("POST", CLI, cuerpo), es_400)
for nombre, v in [("texto '1'", '"1"'), ("decimal 1.5", "1.5"), ("1.0", "1.0"), ("booleano", "true"), ("null", "null"), ("arreglo", "[1]"),
                  ("objeto", "{}"), ("negativo", "-1"), ("cero", "0"), ("int overflow", "2147483648"), ("enorme", "9" * 400),
                  ("exponente 1e0", "1e0"), ("hex", "0x1"), ("con ceros", "01")]:
    check(f"bandera {nombre}", 400, raw("POST", CLI, '{"bandera":%s}' % v), es_400)
check("bandera duplicada (1 y 4): no debe colarse como consulta pública", (400, 401),
      raw("POST", CLI, '{"bandera":1,"bandera":4}'))
check("JSON anidado 20,000 niveles", (400, 413), raw("POST", CLI, '{"bandera":4,"x":' + "[" * 20000 + "]" * 20000 + "}"))
check("cuerpo 70 KB", 413, raw("POST", CLI, '{"bandera":4,"x":"' + "A" * 70000 + '"}'))
check("cuerpo chunked 300 KB", (413, 400), raw("POST", CLI, '{"bandera":4,"x":"' + "A" * 300000 + '"}', chunked=True))
check("clave de 10,000 caracteres", (400, 413), raw("POST", CLI, '{"' + "k" * 10000 + '":1,"bandera":9}'))

print("\n===== 2. CABECERAS Y MÉTODOS =====")
X = {"Content-Type": "application/xml"}
check("XML: bandera 9", (400, 415), raw("POST", CLI, "<LayawayClienteRequest><bandera>9</bandera></LayawayClienteRequest>", X))
check("XML: bandera 4 sin token (¿entra por XML?)", (401, 415), raw("POST", CLI, "<LayawayClienteRequest><bandera>4</bandera></LayawayClienteRequest>", X))
check("XML: bandera como texto 'uno'", (400, 415), raw("POST", CLI, "<x><bandera>uno</bandera></x>", X))
check("XML: entidad externa (XXE)", (400, 415), raw("POST", CLI, '<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><x><bandera>&e;</bandera></x>', X))
check("XML: billion laughs", (400, 415), raw("POST", CLI, '<?xml version="1.0"?><!DOCTYPE l [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;"><!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;"><!ENTITY d "&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;"><!ENTITY e2 "&d;&d;&d;&d;&d;&d;&d;&d;&d;&d;">]><x><bandera>&e2;</bandera></x>', X))
check("XML: login con usuario inexistente", (401, 400, 415), raw("POST", LOGIN, "<LoginRequest><bandera>1</bandera><username>no.existe.zz</username><password>Password123!</password></LoginRequest>", X))
for ct in ["text/plain", "application/xml", "application/x-www-form-urlencoded", "multipart/form-data; boundary=x", None]:
    check(f"Content-Type {ct}", 415, raw("POST", CLI, '{"bandera":9}', {"Content-Type": ct}))
check("Content-Type json;charset=ISO-8859-1", (400,), raw("POST", CLI, '{"bandera":9}', {"Content-Type": "application/json;charset=ISO-8859-1"}), es_400)
check("Accept: application/xml", (406, 400), raw("POST", CLI, '{"bandera":9}', {"Accept": "application/xml"}))
for m in ["GET", "PUT", "DELETE", "PATCH"]:
    check(f"{m} {CLI}", 405, raw(m, CLI, '{"bandera":4}'))
check("TRACE (no debe reflejar la petición)", (405, 403, 400), raw("TRACE", CLI))
check("POST al catálogo", 405, raw("POST", CAT, "{}"))
check("ruta inexistente", 404, raw("GET", "/api/v1/../../etc/passwd"))
check("actuator/env no expuesto", (404, 401, 403), raw("GET", "/actuator/env"))
check("actuator/heapdump no expuesto", (404, 401, 403), raw("GET", "/actuator/heapdump"))

# ======================================================================== 3. Registro: campo por campo
print("\n===== 3. REGISTRO (bandera 1): VALIDACIÓN CAMPO POR CAMPO =====")
B = nuevo()
hoy = date.today()
CONTROL = nuevo()
check("CONTROL: un alta válida se acepta (si falla, el resto de la sección no es concluyente)", 201, raw("POST", CLI, CONTROL))
casos = [
    # nombres
    ("datosPersonales.nombre", ""), ("datosPersonales.nombre", "   "), ("datosPersonales.nombre", "Li"), ("datosPersonales.nombre", "A" * 51),
    ("datosPersonales.nombre", "Juan2"), ("datosPersonales.nombre", "<script>alert(1)</script>"), ("datosPersonales.nombre", "Juan\nPerez"),
    ("datosPersonales.nombre", "Juan  Perez"), ("datosPersonales.nombre", " Juan"), ("datosPersonales.nombre", "Juan​ito"),
    ("datosPersonales.nombre", "Juan‮"), ("datosPersonales.nombre", "Ju\u0000an"), ("datosPersonales.nombre", "' OR 1=1 --"),
    ("datosPersonales.nombre", "Јuan"), ("datosPersonales.nombre", "😀😀😀"), ("datosPersonales.nombre", 12345), ("datosPersonales.nombre", ["Juan"]),
    ("datosPersonales.nombre", {"a": 1}), ("datosPersonales.nombre", True), ("datosPersonales.nombre", None),
    ("datosPersonales.segundoNombre", "Al"), ("datosPersonales.segundoNombre", "<b>"), ("datosPersonales.apellidoPaterno", "Pe"),
    ("datosPersonales.apellidoMaterno", "__QUITAR__"),
    # fecha
    ("datosPersonales.fechaNacimiento", "1995-02-30"), ("datosPersonales.fechaNacimiento", "1995-2-3"), ("datosPersonales.fechaNacimiento", "15/05/1995"),
    ("datosPersonales.fechaNacimiento", "1995-05-15T00:00:00"), ("datosPersonales.fechaNacimiento", (hoy + timedelta(days=1)).isoformat()),
    ("datosPersonales.fechaNacimiento", (hoy.replace(year=hoy.year - 18) + timedelta(days=1)).isoformat()),
    ("datosPersonales.fechaNacimiento", "1800-01-01"), ("datosPersonales.fechaNacimiento", 19950515), ("datosPersonales.fechaNacimiento", ""),
    ("datosPersonales.fechaNacimiento", "0000-00-00"), ("datosPersonales.fechaNacimiento", "+1995-05-15"), ("datosPersonales.fechaNacimiento", "1995-05-15 "),
    ("datosPersonales.fechaNacimiento", [1995, 5, 15]), ("datosPersonales.fechaNacimiento", None),
    # curp / rfc
    ("datosPersonales.curp", "pera900310hgtrrr01"), ("datosPersonales.curp", "PERA900310HGTRRR0"), ("datosPersonales.curp", "PERA900310HGTRRR011"),
    ("datosPersonales.curp", "PERA901310HGTRRR01"), ("datosPersonales.curp", "PERA900332HGTRRR01"), ("datosPersonales.curp", "PERA900310HXXRRR01"),
    ("datosPersonales.curp", "PERA900310HGTAEI01"), ("datosPersonales.curp", "PERA900310 GTRRR01"), ("datosPersonales.curp", "PERA900310XGTRRR01"),
    ("datosPersonales.curp", "PÉRA900310HGTRRR01"), ("datosPersonales.curp", 123456789012345678),
    ("datosPersonales.rfc", "PERA900310AB"), ("datosPersonales.rfc", "PERA901310AB1"), ("datosPersonales.rfc", "pera900310ab1"),
    ("datosPersonales.rfc", "PERA900310AB12"), ("datosPersonales.rfc", "PE RA900310AB"),
    # catálogos
    ("datosPersonales.sexo", "masculino"), ("datosPersonales.sexo", "M"), ("datosPersonales.sexo", "HOMBRE"), ("datosPersonales.sexo", "MASCULINO "),
    ("datosPersonales.sexo", " MASCULINO"), ("datosPersonales.sexo", "OTRO\n"), ("datosPersonales.sexo", "MASCULINO,FEMENINO"),
    ("datosPersonales.nacionalidad", "MEX"), ("datosPersonales.nacionalidad", "mexicana"), ("datosPersonales.nacionalidad", "MEXICANA "),
    ("datosPersonales.nacionalidad", "MEXICANA'; DROP TABLE tb_cliente;--"), ("datosPersonales.nacionalidad", "MEXICANA%"),
    ("datosPersonales.estadoCivil", "UNION  LIBRE"), ("datosPersonales.estadoCivil", "union libre"), ("datosPersonales.estadoCivil", "UNIÓN LIBRE"),
    ("datosPersonales.datosBiometricos", -1), ("datosPersonales.datosBiometricos", 0), ("datosPersonales.datosBiometricos", "123"),
    ("datosPersonales.datosBiometricos", 99999999999999999999),
    # contacto
    ("datosContacto.correo", "a@b"), ("datosContacto.correo", "a@@b.com"), ("datosContacto.correo", "a b@c.com"), ("datosContacto.correo", "<x>@y.com"),
    ("datosContacto.correo", "a" * 250 + "@example.com"), ("datosContacto.correo", "x@example.com\nBcc: y@z.com"), ("datosContacto.correo", ""),
    ("datosContacto.correo", "x@example..com"), ("datosContacto.correo", ".x@example.com"),
    ("datosContacto.telefonoMovil", 418123456), ("datosContacto.telefonoMovil", 41812345678), ("datosContacto.telefonoMovil", "4181234567"),
    ("datosContacto.telefonoMovil", -4181234567), ("datosContacto.telefonoMovil", 4181234567.5), ("datosContacto.telefonoMovil", 1e30),
    ("datosContacto.telefonoMovil", 0), ("datosContacto.telefonoAlt", 12),
    # domicilio
    ("domicilio.codigoPostal", "3780"), ("domicilio.codigoPostal", "378000"), ("domicilio.codigoPostal", "ABCDE"), ("domicilio.codigoPostal", 37800),
    ("domicilio.calle", "__QUITAR__"), ("domicilio.calle", "<img src=x onerror=alert(1)>"), ("domicilio.numeroExterior", "1" * 300),
    ("domicilio.estado", "Guanajuato\r\n"), ("domicilio.pais", ""),
    # ingreso
    ("informacionLaboral.ingresoMensual", "__N:0.00__"), ("informacionLaboral.ingresoMensual", "__N:-1.00__"), ("informacionLaboral.ingresoMensual", "__N:15000.001__"), ("informacionLaboral.ingresoMensual", "__N:15000__"), ("informacionLaboral.ingresoMensual", "__N:15000.0__"),
    ("informacionLaboral.ingresoMensual", "15000.00"), ("informacionLaboral.ingresoMensual", None),
    ("informacionLaboral.ingresoMensual", "__N:1e308__"), ("informacionLaboral.ingresoMensual", "__N:99999999999999999999.99__"), ("informacionLaboral.ingresoMensual", "__N:1e999999999__"),
    # credenciales
    ("loginCredenciales.username", "ab"), ("loginCredenciales.username", "a" * 51), ("loginCredenciales.username", "juan perez"),
    ("loginCredenciales.username", "admin'--"), ("loginCredenciales.username", "jüan"), ("loginCredenciales.username", ""),
    ("loginCredenciales.password", "password"), ("loginCredenciales.password", "PASSWORD123!"), ("loginCredenciales.password", "Password!!"),
    ("loginCredenciales.password", "Password123"), ("loginCredenciales.password", "Pa1!"), ("loginCredenciales.password", "Pass word123!"),
    ("loginCredenciales.password", "Aa1!" + "x" * 69), ("loginCredenciales.password", "Password123!\t"), ("loginCredenciales.password", 12345678),
    ("loginCredenciales.faceIdBiometrico", -5), ("loginCredenciales.faceIdBiometrico", "abc"),
    # secciones
    ("datosPersonales", None), ("datosContacto", "__QUITAR__"), ("domicilio", []), ("informacionLaboral", "texto"),
    ("clienteId", 7), ("filtros", {"curp": "PERA900310HGTRRR01"}),
]
for ruta, valor in casos:
    r = raw("POST", CLI, con(B, ruta, valor))
    esperado = 404 if ruta == "datosPersonales.nacionalidad" and valor in ("MEX",) else 400
    check(f"{ruta} = {json.dumps(valor, ensure_ascii=False)[:45]}", esperado, r, (lambda c: es_400(c)) if esperado == 400 else None)

# ======================================================================== 4. Asignación masiva y unicidad
print("\n===== 4. CAMPOS DEL SERVIDOR Y UNICIDAD =====")
A = nuevo()
falso = copy.deepcopy(A)
falso.update({"rol": "EJECUTIVO", "activo": False, "saldoInicial": 999999, "numeroCuenta": "300000000001", "id": 1, "estatusCuenta": "INACTIVA"})
falso["loginCredenciales"]["rol"] = "EJECUTIVO"
falso["datosPersonales"]["activo"] = False
r = raw("POST", CLI, falso)
check("alta con rol/saldo/activo/numeroCuenta inyectados: se crea con valores del servidor", (201, 400), r,
      lambda c: c.get("codigo") == 1 or (c["data"]["saldoInicial"] == 1000.00 and c["data"]["activo"] is True
                                          and c["data"]["numeroCuenta"] != "300000000001" and c["data"]["estatusCuenta"] == "ACTIVA"))
creado_a = r[0] == 201
if not creado_a:
    creado_a = raw("POST", CLI, A)[0] == 201
lg = raw("POST", LOGIN, {"bandera": 1, "username": A["loginCredenciales"]["username"], "password": "Password123!"})
check("el usuario inyectado sigue siendo CLIENTE", 200, lg, lambda c: c["data"]["rol"] == "CLIENTE")
TA = lg[1]["data"]["token"] if lg[0] == 200 else None

dup = nuevo()
dup["datosPersonales"]["curp"] = A["datosPersonales"]["curp"]
check("CURP duplicada", 409, raw("POST", CLI, dup))
dup = nuevo(); dup["datosPersonales"]["rfc"] = A["datosPersonales"]["rfc"]
check("RFC duplicado", 409, raw("POST", CLI, dup))
dup = nuevo(); dup["datosContacto"]["correo"] = A["datosContacto"]["correo"].upper()
check("correo duplicado en MAYÚSCULAS", 409, raw("POST", CLI, dup))
dup = nuevo(); dup["loginCredenciales"]["username"] = A["loginCredenciales"]["username"]
check("username duplicado", 409, raw("POST", CLI, dup))
dup = nuevo(); dup["loginCredenciales"]["username"] = A["loginCredenciales"]["username"].upper()
r = raw("POST", CLI, dup)
check("username que solo difiere en mayúsculas (¿suplantación?)", (409, 400, 201), r)
if r[0] == 201:
    hallazgos.append(("username distinto solo en mayúsculas se acepta como otro usuario", 201, A["loginCredenciales"]["username"].upper()))
    print("   NOTA: se aceptó un username que solo difiere en mayúsculas")

# ======================================================================== 5. Login
print("\n===== 5. LOGIN =====")
for nombre, cuerpo in [("SQL en usuario", {"bandera": 1, "username": "' OR '1'='1", "password": "x"}),
                       ("usuario como número", {"bandera": 1, "username": 123, "password": "Password123!"}),
                       ("password como objeto", {"bandera": 1, "username": "abc", "password": {"$ne": None}}),
                       ("faceId como texto", {"bandera": 2, "username": "abc", "faceIdBiometrico": "123"}),
                       ("faceId negativo", {"bandera": 2, "username": "abc", "faceIdBiometrico": -1}),
                       ("bandera 3", {"bandera": 3, "username": "abc", "password": "x"}),
                       ("password de 10,000", {"bandera": 1, "username": "abc", "password": "A" * 10000}),
                       ("sin cuerpo útil", {})]:
    check(f"login: {nombre}", 400, raw("POST", LOGIN, cuerpo), es_400)
check("login: usuario inexistente", 401, raw("POST", LOGIN, {"bandera": 1, "username": "no.existe.zz", "password": "Password123!"}))

# ======================================================================== 6. Token
print("\n===== 6. TOKEN =====")
for nombre, h in [("sin Authorization", {}), ("bearer en minúsculas", {"Authorization": "bearer " + (TA or "x")}),
                  ("doble espacio", {"Authorization": "Bearer  " + (TA or "x")}), ("token + basura", {"Authorization": "Bearer " + (TA or "x") + "x"}),
                  ("token de 8 KB", {"Authorization": "Bearer " + "a" * 8000}), ("JWT falso alg none", {"Authorization": "Bearer eyJhbGciOiJub25lIn0.eyJyb2wiOiJFSkVDVVRJVk8ifQ."}),
                  ("Basic", {"Authorization": "Basic YWRtaW46YWRtaW4="}), ("inyección CRLF", {"Authorization": "Bearer abc%0d%0aX: y"})]:
    r = raw("POST", CLI, {"bandera": 4}, h)
    esperado = 200 if nombre in ("bearer en minúsculas", "doble espacio") and TA else (400, 401, 431)
    check(f"bandera 4 {nombre}", esperado if isinstance(esperado, tuple) else (esperado, 401), r)

# ======================================================================== 7. Consulta / actualización como CLIENTE
print("\n===== 7. CLIENTE AUTENTICADO: FILTROS Y ACTUALIZACIÓN =====")
OTRO = nuevo()
raw("POST", CLI, OTRO)
if TA:
    H = {"Authorization": "Bearer " + TA}
    check("cliente sin filtros: solo su registro", 200, raw("POST", CLI, {"bandera": 4}, H),
          lambda c: [k["curp"] for k in c["data"]["clientes"]] == [A["datosPersonales"]["curp"]])
    for nombre, f in [("comodín %", {"curp": "%"}), ("comodín _", {"curp": "_"}), ("curp con espacio", {"curp": "PE RA"}),
                      ("curp vacía", {"curp": ""}), ("curp null", {"curp": None}), ("curp número", {"curp": 123}),
                      ("curp arreglo", {"curp": ["A"]}), ("filtro nombre", {"nombre": "Prueba"}), ("filtro id", {"id": 1}),
                      ("filtros arreglo", ["x"]), ("filtros texto", "x"), ("SQL", {"curp": "' OR 1=1--"}), ("unicode", {"curp": "PÉRA"}),
                      ("curp de 19", {"curp": "A" * 19})]:
        r = raw("POST", CLI, {"bandera": 4, "filtros": f}, H)
        if nombre == "curp null":
            check(f"filtro {nombre}: equivale a sin filtro (solo el suyo)", 200, r, lambda c: len(c["data"]["clientes"]) == 1)
        else:
            check(f"filtro {nombre}", 400, r, es_400)
    check("cliente consulta CURP del otro", 404, raw("POST", CLI, {"bandera": 4, "filtros": {"curp": OTRO["datosPersonales"]["curp"]}}, H))
    check("cliente consulta CURP del otro en minúsculas", 404, raw("POST", CLI, {"bandera": 4, "filtros": {"curp": OTRO["datosPersonales"]["curp"].lower()}}, H))
    check("filtros duplicados en el JSON (propio + ajeno)", (404, 400, 200), raw("POST", CLI,
          '{"bandera":4,"filtros":{"curp":"%s"},"filtros":{"curp":"%s"}}' % (A["datosPersonales"]["curp"], OTRO["datosPersonales"]["curp"]), H),
          lambda c: c.get("codigo") != 0 or all(k["curp"] == A["datosPersonales"]["curp"] for k in c["data"]["clientes"]))
    yo = {"filtros": {"curp": A["datosPersonales"]["curp"]}}
    upd = {"bandera": 2, **yo, "datosContacto": {"correo": OTRO["datosContacto"]["correo"], "telefonoMovil": 4181112233}}
    check("actualizar con el correo del otro", 409, raw("POST", CLI, upd, H))
    dp = copy.deepcopy(A["datosPersonales"]); dp["curp"] = OTRO["datosPersonales"]["curp"]
    check("actualizar intentando poner la CURP del otro", 400, raw("POST", CLI, {"bandera": 2, **yo, "datosPersonales": dp}, H))
    check("actualizar con datosPersonales parciales (solo nombre)", 400, raw("POST", CLI, {"bandera": 2, **yo, "datosPersonales": {"nombre": "Nuevo"}}, H), es_400)
    check("actualizar con loginCredenciales de otro usuario", 400, raw("POST", CLI, {"bandera": 2, **yo,
          "loginCredenciales": {"username": OTRO["loginCredenciales"]["username"], "password": "Hack#2026xx"}}, H))
    check("actualizar con llave del otro en minúsculas", 404, raw("POST", CLI, {"bandera": 2, "filtros": {"curp": OTRO["datosPersonales"]["curp"].lower()},
          "datosContacto": {"correo": OTRO["datosContacto"]["correo"], "telefonoMovil": 4181112233}}, H))
    check("baja como cliente", 403, raw("POST", CLI, {"bandera": 3, **yo}, H))
    check("bandera 4 con datosPersonales", 400, raw("POST", CLI, {"bandera": 4, "datosPersonales": A["datosPersonales"]}, H), es_val)
    check("bandera 2 con clienteId", 400, raw("POST", CLI, {"bandera": 2, **yo, "clienteId": 1}, H), es_val)

print(f"\nTOTAL {len(res)}  PASS {sum(map(bool, res))}  FAIL {len(res) - sum(map(bool, res))}")
if hallazgos:
    print("\n=== HALLAZGOS ===")
    for h in hallazgos:
        print(" -", h)
