"""
Batería de pruebas de seguridad, validación, concurrencia y carga para los endpoints
POST /api/v1/layaway/cliente y POST /api/v1/auth/login.

Simula lo que haría JMeter y un atacante: datos inválidos, tipos JSON incorrectos, cuerpos enormes
(con y sin Content-Length), duplicados, condiciones de carrera, fuerza bruta, enumeración de usuarios
(por código y por tiempo de respuesta) y carga concurrente.

Uso (con la API levantada; solo usa la librería estándar de Python 3.10+):
    python -X utf8 pruebas_seguridad_carga.py 8081

ADVERTENCIA: inserta cientos de clientes de prueba. Ejecutar contra una base de datos de pruebas.
"""
import json, http.client, random, string, sys, time, copy, socket
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta

HOST, PORT = "localhost", int(sys.argv[1]) if len(sys.argv) > 1 else 8081
CLI, LOGIN = "/api/v1/layaway/cliente", "/api/v1/auth/login"
results = []


def req(path, body=None, raw=None, headers=None, method="POST"):
    c = http.client.HTTPConnection(HOST, PORT, timeout=60)
    h = {"Content-Type": "application/json"}
    if headers: h.update(headers)
    data = raw if raw is not None else (dumps(body).encode() if body is not None else None)
    c.request(method, path, body=data, headers=h)
    r = c.getresponse(); txt = r.read().decode("utf-8", "replace"); c.close()
    try: j = json.loads(txt)
    except Exception: j = txt
    return r.status, j


import re
def N(txt): return "@@N:" + txt + "@@"
def dumps(o): return re.sub(r'"@@N:([^@]*)@@"', lambda m: m.group(1), json.dumps(o))


def rnd_letters(n): return "".join(random.choice("BCDFGHJKLMNPQRSTVWXZ") for _ in range(n))


def curp_rfc():
    l4 = rnd_letters(4); y = random.randint(60, 99); m = random.randint(1, 12); d = random.randint(1, 28)
    f = f"{y:02d}{m:02d}{d:02d}"
    curp = f"{l4}{f}H" + "DF" + rnd_letters(3) + "0" + str(random.randint(0, 9))
    rfc = f"{l4}{f}" + "".join(random.choice(string.ascii_uppercase + string.digits) for _ in range(3))
    return curp, rfc


def base(**over):
    curp, rfc = curp_rfc()
    u = "".join(random.choice(string.ascii_lowercase) for _ in range(10))
    b = {
        "bandera": 1,
        "datosPersonales": {"nombre": "Juan", "apellidoPaterno": "Perez", "apellidoMaterno": "Gomez",
                             "fechaNacimiento": "1995-05-15", "curp": curp, "rfc": rfc, "sexo": "MASCULINO",
                             "nacionalidad": "MEXICANA", "estadoCivil": "SOLTERO"},
        "datosContacto": {"correo": f"{u}@mail.com", "telefonoMovil": 5512345678},
        "domicilio": {"calle": "Reforma", "numeroExterior": "100", "colonia": "Centro", "municipio": "Cuauhtemoc",
                      "estado": "CDMX", "codigoPostal": "06000", "pais": "Mexico"},
        "informacionLaboral": {"ocupacion": "Dev", "empresa": "X", "ingresoMensual": N("30000.00")},
        "loginCredenciales": {"username": u, "password": "Password123!", "faceIdBiometrico": random.randint(1, 10**12)},
    }
    for k, v in over.items():
        sec, _, f = k.partition("__")
        if f:
            if v is DEL: b[sec].pop(f, None)
            else: b[sec][f] = v
        else:
            if v is DEL: b.pop(sec, None)
            else: b[sec] = v
    return b


DEL = object()


def check(name, status, body, expect):
    ok = status in expect if isinstance(expect, (list, tuple, set)) else status == expect
    msg = body.get("mensaje") if isinstance(body, dict) else str(body)[:150]
    results.append((ok, name, status, expect, msg))
    print(("PASS " if ok else "FAIL ") + f"[{status} exp {expect}] {name} :: {str(msg)[:170]}")
    return body


def ins(name, expect, **over):
    b = base(**over)
    s, j = req(CLI, b)
    check(name, s, j, expect)
    return b, s, j


hoy = date.today()
def years_ago(y, extra_days=0):
    try: d = hoy.replace(year=hoy.year - y)
    except ValueError: d = hoy.replace(year=hoy.year - y, day=28)
    return (d + timedelta(days=extra_days)).isoformat()

print("===== REGISTRO: caso feliz y reglas del documento =====")
okb, s, okj = ins("alta valida", 201)
cid = okj["data"]["clienteId"] if s == 201 else None
ins("nombre de 2 letras (minimo 3)", 400, datosPersonales__nombre="Li")
ins("nombre de 3 letras", 201, datosPersonales__nombre="Ana")
ins("apellidos de 2 letras (minimo 3)", 400, datosPersonales__apellidoPaterno="Ek", datosPersonales__apellidoMaterno="Ma")
ins("nombre 1 letra", 400, datosPersonales__nombre="J")
ins("nombre 51 letras", 400, datosPersonales__nombre="A" * 51)
ins("nombre con digitos", 400, datosPersonales__nombre="Juan2")
ins("nombre con salto de linea", 400, datosPersonales__nombre="Juan\nPerez")
ins("nombre con tabulador", 400, datosPersonales__nombre="Juan\tPerez")
ins("nombre solo espacios", 400, datosPersonales__nombre="     ")
ins("nombre con espacios al borde", (201, 400), datosPersonales__nombre="  Juan  ")
ins("segundo nombre vacio ''", (201, 400), datosPersonales__segundoNombre="")
ins("segundo nombre 1 letra", 400, datosPersonales__segundoNombre="X")
ins("nombre con <script>", 400, datosPersonales__nombre="<script>")
ins("nombre unicode combinante (e + acento)", (201, 400), datosPersonales__nombre="José")
ins("sin apellido materno", 400, datosPersonales__apellidoMaterno=DEL)

ins("CURP minusculas", (400, 201), datosPersonales__curp=base()["datosPersonales"]["curp"].lower())
ins("CURP 17 chars", 400, datosPersonales__curp="PERJ950515HDFRMN0")
ins("CURP mes 13", 400, datosPersonales__curp="PERJ951315HDFRMN01")
ins("CURP dia 32", 400, datosPersonales__curp="PERJ950532HDFRMN01")
ins("CURP estado inexistente XX", 400, datosPersonales__curp="PERJ950515HXXRMN01")
_b = base(); _b["datosPersonales"]["rfc"] = _b["datosPersonales"]["rfc"][1:]
_s, _j = req(CLI, _b); check("RFC 12 chars (doc: 12 o 13)", _s, _j, 201)
ins("RFC 11 chars", 400, datosPersonales__rfc="ABC950515A1")
ins("RFC mes 13", 400, datosPersonales__rfc="PERJ951315AB1")
ins("RFC 14 chars", 400, datosPersonales__rfc="PERJ950515AB12")

ins("correo sin TLD a@b", 400, datosContacto__correo="a@b")
ins("correo invalido", 400, datosContacto__correo="no-es-correo")
ins("correo 101 chars", 400, datosContacto__correo="a" * 92 + "@mail.com")
ins("correo con espacios", 400, datosContacto__correo="ju an@mail.com")

ins("fecha futura", 400, datosPersonales__fechaNacimiento=(hoy + timedelta(days=1)).isoformat())
ins("17 anios 364 dias", 400, datosPersonales__fechaNacimiento=years_ago(18, 1))
ins("exactamente 18 anios hoy", 201, datosPersonales__fechaNacimiento=years_ago(18))
ins("fecha 1995-02-31 (no existe)", 400, datosPersonales__fechaNacimiento="1995-02-31")
ins("fecha 1995-2-3", 400, datosPersonales__fechaNacimiento="1995-2-3")
ins("fecha 15/05/1995", 400, datosPersonales__fechaNacimiento="15/05/1995")
ins("fecha año 0001", 400, datosPersonales__fechaNacimiento="0001-01-01")
ins("fecha como numero", 400, datosPersonales__fechaNacimiento=19950515)

ins("telefono 9 digitos", 400, datosContacto__telefonoMovil=551234567)
ins("telefono 11 digitos", 400, datosContacto__telefonoMovil=55123456789)
ins("telefono como string", 400, datosContacto__telefonoMovil="5512345678")
ins("telefono decimal", 400, datosContacto__telefonoMovil=5512345678.5)
ins("telefono negativo", 400, datosContacto__telefonoMovil=-5512345678)
ins("telefono gigante (overflow long)", 400, datosContacto__telefonoMovil=99999999999999999999999)
ins("telefono alt 9 digitos", 400, datosContacto__telefonoAlt=551234567)

ins("CP 4 digitos", 400, domicilio__codigoPostal="0600")
ins("CP 6 digitos", 400, domicilio__codigoPostal="060000")
ins("CP con letras", 400, domicilio__codigoPostal="06A00")
ins("CP numerico (sin comillas)", (400, 201), domicilio__codigoPostal=6000)
ins("CP con salto de linea al final", 400, domicilio__codigoPostal="06000\n")
ins("sin domicilio", 400, domicilio=DEL)
ins("calle con <script>", (400, 201), domicilio__calle="<script>alert(1)</script>")
ins("calle con caracteres de control", 400, domicilio__calle="Ref\u0000orma")

ins("ingreso 0.00", 400, informacionLaboral__ingresoMensual=N("0.00"))
ins("ingreso negativo", 400, informacionLaboral__ingresoMensual=N("-100.00"))
ins("ingreso 1e2", (400, 201), informacionLaboral__ingresoMensual=1e2)
ins("ingreso como string", 400, informacionLaboral__ingresoMensual="30000.00")
ins("ingreso 13 enteros", 400, informacionLaboral__ingresoMensual=N("1234567890123.00"))
t0 = time.time()
_, raw = None, dumps(base()).replace('"ingresoMensual": 30000.00', '"ingresoMensual": 1e999999999')
s, j = req(CLI, raw=raw.encode())
check(f"ingreso 1e999999999 (DoS BigDecimal) {time.time()-t0:.1f}s", s, j, 400)
raw = dumps(base()).replace('"ingresoMensual": 30000.00', '"ingresoMensual": 1e-999999999')
t0 = time.time(); s, j = req(CLI, raw=raw.encode())
check(f"ingreso 1e-999999999 {time.time()-t0:.1f}s", s, j, 400)

ins("sexo invalido", 400, datosPersonales__sexo="X")
ins("estado civil invalido", 400, datosPersonales__estadoCivil="COMPLICADO")
ins("nacionalidad no catalogo", 404, datosPersonales__nacionalidad="MARCIANA")
ins("nacionalidad exacta ESPAÑOLA", 201, datosPersonales__nacionalidad="ESPAÑOLA")
for _v, _e in [("mexicana", 400), ("Mexicana", 400), ("mex", 400), ("MEX", 404), (" MEXICANA", 400)]:
    ins(f"catalogo estricto: nacionalidad {_v!r}", _e, datosPersonales__nacionalidad=_v)
for _v in ["masculino", "Masculino", "H", "HOMBRE", "M"]:
    ins(f"catalogo estricto: sexo {_v!r}", 400, datosPersonales__sexo=_v)
for _v in ["SOLTERA", "CASADA", "soltero", "UNION_LIBRE", "union libre"]:
    ins(f"catalogo estricto: estado civil {_v!r}", 400, datosPersonales__estadoCivil=_v)
for _v in ["FEMENINO", "OTRO"]:
    ins(f"catalogo: sexo {_v!r}", 201, datosPersonales__sexo=_v)
for _v in ["CASADO", "DIVORCIADO", "VIUDO", "UNION LIBRE"]:
    ins(f"catalogo: estado civil {_v!r}", 201, datosPersonales__estadoCivil=_v)

print("===== UNICIDAD =====")
d = okb["datosPersonales"]
ins("CURP duplicada", 409, datosPersonales__curp=d["curp"])
ins("RFC duplicado", 409, datosPersonales__rfc=d["rfc"])
ins("correo duplicado", 409, datosContacto__correo=okb["datosContacto"]["correo"])
ins("correo duplicado en MAYUSCULAS", 409, datosContacto__correo=okb["datosContacto"]["correo"].upper())
ins("correo duplicado con espacios", (409, 400), datosContacto__correo=" " + okb["datosContacto"]["correo"] + " ")
ins("username duplicado", 409, loginCredenciales={"username": okb["loginCredenciales"]["username"], "password": "Password123!"})
ins("username con salto de linea", 400, loginCredenciales={"username": "evil\nINFO fake log", "password": "Password123!"})
ins("username con espacios", 400, loginCredenciales={"username": "a b", "password": "Password123!"})
ins("password '1' (debil)", 400, loginCredenciales={"username": "weakpwuser" + rnd_letters(4).lower(), "password": "1"})
ins("faceId negativo", 400, loginCredenciales={"username": "neg" + rnd_letters(6).lower(), "password": "Password123!", "faceIdBiometrico": -1})

print("===== BANDERA / JSON =====")
ins("bandera como string '1'", 400, bandera="1")
ins("bandera 1.0", 400, bandera=1.0)
ins("bandera 4", 400, bandera=4)
ins("bandera null", 400, bandera=None)
ins("bandera 1 con clienteId 5", 400, clienteId=5)
s, j = req(CLI, raw=b""); check("cuerpo vacio", s, j, 400)
s, j = req(CLI, raw=b"{"); check("JSON roto", s, j, 400)
s, j = req(CLI, raw=b"[]"); check("JSON arreglo", s, j, 400)
s, j = req(CLI, raw=b"null"); check("JSON null", s, j, 400)
s, j = req(CLI, raw=json.dumps(base()).encode(), headers={"Content-Type": "text/plain"}); check("content-type text/plain", s, j, 415)
s, j = req(CLI, method="GET"); check("GET no permitido", s, j, 405)
s, j = req(CLI, raw=("[" * 5000 + "]" * 5000).encode()); check("anidamiento 5000", s, j, 400)
big = base(); big["x"] = "A" * 200000
s, j = req(CLI, raw=dumps(big).encode()); check("cuerpo 200KB con Content-Length", s, j, 413)
dup = dumps(base())[:-1] + ', "bandera": 3}'
s, j = req(CLI, raw=dup.encode()); check("clave 'bandera' duplicada en JSON", s, j, 400)


def chunked(path, total_mb):
    sck = socket.create_connection((HOST, PORT), timeout=60)
    sck.sendall(f"POST {path} HTTP/1.1\r\nHost: x\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n".encode())
    chunk = b'{"bandera":1,"x":"' + b"A" * (1024 * 1024)
    try:
        sck.sendall(f"{len(chunk):x}\r\n".encode() + chunk + b"\r\n")
        filler = b"A" * (1024 * 1024)
        for _ in range(total_mb):
            sck.sendall(f"{len(filler):x}\r\n".encode() + filler + b"\r\n")
        tail = b'"}'
        sck.sendall(f"{len(tail):x}\r\n".encode() + tail + b"\r\n0\r\n\r\n")
    except OSError as e:
        pass
    data = b""
    try:
        while True:
            d = sck.recv(65536)
            if not d: break
            data += d
            if b"\r\n\r\n" in data and len(data) > 200: break
    except OSError: pass
    sck.close()
    line = data.split(b"\r\n", 1)[0].decode(errors="replace")
    return line, data[-200:]

t0 = time.time(); line, tail = chunked(CLI, 1)
st = int(line.split()[1]) if line.startswith("HTTP") else -1
check(f"cuerpo chunked 2MB sin Content-Length ({time.time()-t0:.1f}s)", st, tail.decode(errors='replace'), 413)

print("===== ACTUALIZAR / BAJA =====")
upd = copy.deepcopy(okb); upd["bandera"] = 2; upd["clienteId"] = cid
upd["datosPersonales"]["curp"] = curp_rfc()[0]
s, j = req(CLI, upd); check("bandera 2 intentando cambiar CURP", s, j, 400)
upd = copy.deepcopy(okb); upd["bandera"] = 2; upd["clienteId"] = cid
upd["datosPersonales"]["rfc"] = curp_rfc()[1]
s, j = req(CLI, upd); check("bandera 2 intentando cambiar RFC", s, j, 400)
upd = copy.deepcopy(okb); upd["bandera"] = 2; upd["clienteId"] = cid; upd["datosPersonales"]["nombre"] = "Pedro"
s, j = req(CLI, upd); check("bandera 2 actualizacion valida", s, j, 200)
s, j = req(CLI, {"bandera": 2, "clienteId": cid, "domicilio": {"calle": "Nueva", "numeroExterior": "1", "colonia": "C", "municipio": "M", "estado": "E", "codigoPostal": "12345", "pais": "MX"}})
check("bandera 2 solo domicilio", s, j, 200)
s, j = req(CLI, {"bandera": 2, "clienteId": 999999999}); check("bandera 2 cliente inexistente", s, j, 404)
s, j = req(CLI, {"bandera": 2, "clienteId": -1}); check("bandera 2 id negativo", s, j, 400)
s, j = req(CLI, {"bandera": 2}); check("bandera 2 sin id", s, j, 400)
otro = ins("alta para probar correo ajeno", 201)[0]
upd = copy.deepcopy(okb); upd["bandera"] = 2; upd["clienteId"] = cid; upd["datosContacto"]["correo"] = otro["datosContacto"]["correo"]
s, j = req(CLI, upd); check("bandera 2 robar correo de otro cliente", s, j, 409)
s, j = req(CLI, {"bandera": 3, "clienteId": cid}); check("bandera 3 baja logica", s, j, 200)
s, j = req(CLI, {"bandera": 3, "clienteId": cid}); check("bandera 3 baja repetida", s, j, (200, 409))
upd = copy.deepcopy(okb); upd["bandera"] = 2; upd["clienteId"] = cid
s, j = req(CLI, upd); check("bandera 2 sobre cliente inactivo", s, j, 403)

print("===== LOGIN =====")
u = otro["loginCredenciales"]["username"]
s, j = req(LOGIN, {"bandera": 1, "username": u, "password": "Password123!"}); check("login valido", s, j, 200)
s, j = req(LOGIN, {"bandera": 1, "username": okb["loginCredenciales"]["username"], "password": "Password123!"}); check("login cliente dado de baja", s, j, (401, 403))
s, j = req(LOGIN, {"bandera": 2, "username": u, "faceIdBiometrico": otro["loginCredenciales"]["faceIdBiometrico"]}); check("login faceId valido", s, j, 200)
nopw = ins("alta SIN password", 201, loginCredenciales=DEL)[0]
s, j = req(LOGIN, {"bandera": 1, "username": nopw["datosPersonales"]["curp"].lower(), "password": "DefaultPassword123!"})
check("login con password por defecto 'DefaultPassword123!'", s, j, 401)
s1, _ = req(LOGIN, {"bandera": 1, "username": "noexiste_zz", "password": "x"})
s2, _ = req(LOGIN, {"bandera": 1, "username": u, "password": "mala"})
check(f"enumeracion de usuarios (inexistente={s1} vs mala pw={s2})", s1, None, s2)
codes = [req(LOGIN, {"bandera": 1, "username": u, "password": f"mala{i}"})[0] for i in range(12)]
check(f"fuerza bruta 12 intentos -> {codes}", codes[-1], None, (423, 429))
s, j = req(LOGIN, {"bandera": 1, "username": u, "password": "Password123!"}); check("login tras bloqueo (debe seguir bloqueado)", s, j, (423, 429))
s, j = req(LOGIN, {"bandera": 3, "username": u}); check("login bandera 3", s, j, 400)
s, j = req(LOGIN, {"bandera": "1", "username": u, "password": "x"}); check("login bandera string", s, j, 400)
s, j = req(LOGIN, {"bandera": 1, "username": u}); check("login sin password", s, j, 400)
s, j = req(LOGIN, {"bandera": 2, "username": u}); check("login sin faceId", s, j, 400)
s, j = req(LOGIN, {"bandera": 1, "username": "a\nb", "password": "x"}); check("login username con salto de linea", s, j, (400, 401))

tu = ins("alta para prueba de tiempos", 201)[0]["loginCredenciales"]["username"]
def tiempo(body, n=4):
    t0 = time.time()
    for _ in range(n): req(LOGIN, body)
    return (time.time() - t0) / n * 1000
t_inex = tiempo({"bandera": 1, "username": "noexiste_tiempo", "password": "Password123!"})
t_mala = tiempo({"bandera": 1, "username": tu, "password": "Mala12345!"})
ratio = t_inex / t_mala if t_mala else 0
check(f"enumeracion por tiempo: inexistente {t_inex:.0f} ms vs pw incorrecta {t_mala:.0f} ms (ratio {ratio:.2f})",
      0 if 0.5 <= ratio <= 2 else 1, None, 0)

print("===== CONCURRENCIA =====")
same = base()
def post_same(_):
    b = copy.deepcopy(same); b["loginCredenciales"]["username"] = "".join(random.choice(string.ascii_lowercase) for _ in range(12))
    b["datosContacto"]["correo"] = b["loginCredenciales"]["username"] + "@m.com"
    return req(CLI, b)[0]
with ThreadPoolExecutor(30) as ex: cs = list(ex.map(post_same, range(30)))
check(f"30 altas simultaneas misma CURP -> 201:{cs.count(201)} 409:{cs.count(409)} otros:{[c for c in cs if c not in (201,409)]}",
      0 if cs.count(201) == 1 and cs.count(409) == 29 else 1, None, 0)

def carga(_):
    return req(CLI, base())[0]
t0 = time.time()
with ThreadPoolExecutor(100) as ex: cs = list(ex.map(carga, range(600)))
dt = time.time() - t0
bad = [c for c in cs if c != 201]
check(f"carga 600 altas / 100 hilos en {dt:.1f}s ({600/dt:.0f} req/s) no-201: {sorted(set(bad))} x{len(bad)}", len(bad), None, 0)

def logins(_):
    return req(LOGIN, {"bandera": 1, "username": "noexiste_" + str(_), "password": "x"})[0]
with ThreadPoolExecutor(100) as ex: cs = list(ex.map(logins, range(400)))
check(f"carga 400 logins -> {sorted(set(cs))}", 0 if all(c < 500 for c in cs) else 1, None, 0)

print()
fails = [r for r in results if not r[0]]
print(f"TOTAL {len(results)}  PASS {len(results)-len(fails)}  FAIL {len(fails)}")
for r in fails: print("  FAIL", r[1], "->", r[2], "(esperado", r[3], ")")
