"""
Pruebas de la consulta de clientes (bandera 4) contra la API en ejecución: sin filtros, filtros llave
(clienteId, curp, rfc, numeroCuenta), comillas, valores en blanco, campos no permitidos y volumen.

Uso:  python -X utf8 pruebas_consulta_bandera4.py 8081
ADVERTENCIA: inserta clientes de prueba. Ejecutar contra una base de datos de pruebas.
"""
import json, http.client, random, string, sys, time

PORT = int(sys.argv[1])
CLI = "/api/v1/layaway/cliente"
res = []


def post(raw):
    c = http.client.HTTPConnection("localhost", PORT, timeout=60)
    c.request("POST", CLI, body=raw.encode("utf-8") if isinstance(raw, str) else json.dumps(raw).encode("utf-8"),
              headers={"Content-Type": "application/json"})
    r = c.getresponse()
    return r.status, json.loads(r.read().decode("utf-8"))


def alta(nombre, ap, curp, rfc, correo, user):
    body = {"bandera": 1,
            "datosPersonales": {"nombre": nombre, "apellidoPaterno": ap, "apellidoMaterno": "Lopez",
                                "fechaNacimiento": "1990-03-10", "curp": curp, "rfc": rfc, "sexo": "MASCULINO",
                                "nacionalidad": "MEXICANA", "estadoCivil": "SOLTERO"},
            "datosContacto": {"correo": correo, "telefonoMovil": 4181234567},
            "domicilio": {"calle": "Hidalgo", "numeroExterior": "1", "colonia": "Centro", "municipio": "DH",
                          "estado": "Guanajuato", "codigoPostal": "37800", "pais": "MEXICO"},
            "informacionLaboral": {"ocupacion": "Dev", "empresa": "X", "ingresoMensual": "@@15000.00@@"},
            "loginCredenciales": {"username": user, "password": "Password123!"}}
    raw = json.dumps(body).replace('"@@15000.00@@"', "15000.00")
    s, j = post(raw)
    assert s == 201, (s, j)
    return j["data"]


def check(nombre, esperado, status, cuerpo, extra=None, condicion=None):
    ok = status == esperado and (condicion(cuerpo) if condicion else True)
    res.append(ok)
    msg = cuerpo.get("mensaje") if isinstance(cuerpo, dict) else cuerpo
    print(("PASS " if ok else "FAIL ") + f"[{status} esp {esperado}] {nombre}" + (f" -> {extra}" if extra else "") + f" :: {str(msg)[:150]}")


def consulta(filtros):
    return post(json.dumps({"bandera": 4, "filtros": filtros}) if filtros is not None else json.dumps({"bandera": 4}))


# --- datos de prueba: 3 clientes con CURP/RFC distintos y dos que comparten fragmentos
a = alta("Fernando", "Perez", "PEPF900310HGTRRR01", "PEPF9003101A1", "fer@mail.com", "fernando.u")
b = alta("Beatriz", "Gomez", "GOMB850512MGTMRT02", "GOMB8505122B2", "bea@mail.com", "beatriz.u")
c = alta("Carlos", "Ruiz", "RUCC950101HGTZRR03", "RUCC9501013C3", "car@mail.com", "carlos.u")
ids = [a["curp"], b["curp"], c["curp"]]
cuentas = [a["numeroCuenta"], b["numeroCuenta"], c["numeroCuenta"]]
print("clientes de prueba:", ids)

print("===== SIN FILTROS =====")
s, j = consulta(None)
check("bandera 4 sin 'filtros' -> todos", 200, s, j, f"{len(j['data']['clientes'])} clientes", lambda x: len(x["data"]["clientes"]) == 3)
s, j = consulta({})
check("bandera 4 con filtros {} -> todos", 200, s, j, None, lambda x: x["data"]["totalCoincidencias"] == 3)
s, j = post('{"bandera": 4, "filtros": null}')
check("bandera 4 con filtros null -> todos", 200, s, j, None, lambda x: len(x["data"]["clientes"]) == 3)
s, j = post('{"bandera": 4, "filtros": {"curp": null, "rfc": null}}')
check("campos null -> todos", 200, s, j, None, lambda x: len(x["data"]["clientes"]) == 3)

print("===== FILTROS (contiene, sin distinguir mayusculas) =====")
s, j = consulta({"curp": "PEPF"})
check("curp 'PEPF' -> 1", 200, s, j, None, lambda x: [k["curp"] for k in x["data"]["clientes"]] == [ids[0]])
s, j = consulta({"curp": "pepf90"})
check("curp en minusculas 'pepf90' -> 1", 200, s, j, None, lambda x: len(x["data"]["clientes"]) == 1)
s, j = consulta({"curp": "HGTRR"})
check("curp contiene 'HGTRR' (mitad del texto) -> 1", 200, s, j, None, lambda x: len(x["data"]["clientes"]) >= 1)
s, j = consulta({"curp": "G"})
check("curp 'G' (una letra) -> todos los que la contengan", 200, s, j, f"{len(j['data']['clientes'])}", lambda x: len(x["data"]["clientes"]) == 3)
s, j = consulta({"curp": "ZZZZ"})
check("curp sin coincidencias -> lista vacia", 200, s, j, None, lambda x: x["data"]["clientes"] == [] and x["data"]["totalCoincidencias"] == 0)
s, j = consulta({"rfc": "gomb85"})
check("rfc 'gomb85' -> 1", 200, s, j, None, lambda x: [k["curp"] for k in x["data"]["clientes"]] == [ids[1]])
s, j = consulta({"numeroCuenta": cuentas[2][:6]})
check("numeroCuenta (prefijo de 6) -> contiene al cliente 3", 200, s, j, None, lambda x: ids[2] in [k["curp"] for k in x["data"]["clientes"]])
s, j = consulta({"numeroCuenta": cuentas[2]})
check("numeroCuenta completo -> 1", 200, s, j, None, lambda x: [k["curp"] for k in x["data"]["clientes"]] == [ids[2]] and x["data"]["clientes"][0]["cuentas"][0]["numeroCuenta"] == cuentas[2])
s, j = consulta({"curp": "PEPF", "rfc": "GOMB"})
check("curp + rfc que no coinciden en el mismo cliente (AND) -> 0", 200, s, j, None, lambda x: x["data"]["clientes"] == [])
s, j = consulta({"curp": "PEPF", "rfc": "PEPF90", "numeroCuenta": cuentas[0][:4]})
check("los 4 filtros a la vez (AND) -> 1", 200, s, j, None, lambda x: len(x["data"]["clientes"]) == 1)

print("===== DATOS DEVUELTOS =====")
s, j = consulta({"curp": ids[0]})
k = j["data"]["clientes"][0]
check("no expone biometricos ni credenciales", 200, s, j, None,
      lambda x: not any(n in k for n in ("clienteId", "datosBiometricos", "passwordHash", "faceId", "username")))
check("trae nombre completo, correo, activo y cuenta", 200, s, j, None,
      lambda x: k["nombreCompleto"] == "Fernando Perez Lopez" and k["correo"] == "fer@mail.com" and k["activo"] is True and k["cuentas"][0]["saldo"] == 1000)
s, j = post(json.dumps({"bandera": 1}))
check("respuesta de otras banderas NO trae campos de consulta", 400, s, j)

print("===== COMILLAS =====")
for nombre, raw in [("curp numerico", '{"bandera":4,"filtros":{"curp":123}}'),
                    ("rfc numerico", '{"bandera":4,"filtros":{"rfc":9505}}'),
                    ("numeroCuenta sin comillas", f'{{"bandera":4,"filtros":{{"numeroCuenta":{cuentas[0]}}}}}'),
                    ("curp booleano", '{"bandera":4,"filtros":{"curp":true}}'),
                    ("curp arreglo", '{"bandera":4,"filtros":{"curp":["PEPF"]}}'),
                    ("clienteId entre comillas", '{"bandera":4,"filtros":{"clienteId":"1"}}')]:
    s, j = post(raw)
    check(f"{nombre} -> 400", 400, s, j)

print("===== EN BLANCO / FORMATO =====")
for nombre, f in [("curp vacia ''", {"curp": ""}), ("curp solo espacios", {"curp": "   "}), ("rfc vacio", {"rfc": ""}),
                  ("numeroCuenta vacio", {"numeroCuenta": ""}), ("numeroCuenta con letras", {"numeroCuenta": "12AB"}),
                  ("curp con comodin %", {"curp": "%"}), ("curp con guion bajo _", {"curp": "_"}),
                  ("curp con espacio", {"curp": "PE PF"}), ("curp con comilla simple", {"curp": "'; DROP TABLE tb_cliente;--"}),
                  ("curp de 19 caracteres", {"curp": "A" * 19}), ("clienteId 0", {"clienteId": 0}), ("clienteId negativo", {"clienteId": -1})]:
    s, j = consulta(f)
    check(f"{nombre} -> 400", 400, s, j)

print("===== SOLO CAMPOS LLAVE =====")
for nombre, f in [("nombre", {"nombre": "f"}), ("correo", {"correo": "fer@mail.com"}), ("activo", {"activo": True}),
                  ("telefonoMovil", {"telefonoMovil": "4181234567"}), ("clienteId", {"clienteId": 1}), ("curp valida + nombre", {"curp": "PEPF", "nombre": "f"})]:
    s, j = consulta(f)
    check(f"filtro '{nombre}' no permitido -> 400 (no devuelve todos)", 400, s, j)

print("===== OTRAS SECCIONES CON BANDERA 4 =====")
for nombre, raw in [("clienteId de nivel superior", '{"bandera":4,"clienteId":1}'),
                    ("datosPersonales", '{"bandera":4,"datosPersonales":{"curp":"PEPF900310HGTRRR01"}}'),
                    ("loginCredenciales", '{"bandera":4,"loginCredenciales":{"username":"fernando.u"}}')]:
    s, j = post(raw)
    check(f"{nombre} -> 400 (no devuelve todos)", 400, s, j)

print("===== OTRAS BANDERAS SIGUEN IGUAL =====")
s, j = post('{"bandera":5}')
check("bandera 5 -> 400", 400, s, j)
s, j = post(json.dumps({"bandera": 3, "filtros": {"curp": ids[2]}}))
check("baja logica (bandera 3) sigue funcionando", 200, s, j, None, lambda x: x["data"]["activo"] is False and "clientes" not in x["data"])
s, j = consulta({"curp": ids[2]})
check("consulta muestra al cliente dado de baja como inactivo", 200, s, j, None,
      lambda x: x["data"]["clientes"][0]["activo"] is False and x["data"]["clientes"][0]["cuentas"][0]["estatus"] == "INACTIVA")

print("===== VOLUMEN: todos los clientes sin filtros =====")
t0 = time.time()
for i in range(30):
    alta("Carga", "Masiva", "".join(random.choice("BCDFGHJKLMNPQRSTVWXZ") for _ in range(4)) + "900101HGTRRR" + "0" + str(i % 10),
         "".join(random.choice("BCDFGHJKLMNPQRSTVWXZ") for _ in range(4)) + "9001011A" + str(i % 10),
         f"carga{i}{random.randint(1000,9999)}@mail.com", f"carga{i}.{random.randint(1000,9999)}")
t1 = time.time()
s, j = consulta(None)
check(f"sin filtros con {len(j['data']['clientes'])} clientes ({(time.time()-t1)*1000:.0f} ms)", 200, s, j, None,
      lambda x: x["data"]["totalCoincidencias"] == len(x["data"]["clientes"]) and x["data"]["resultadosTruncados"] is False)

print(f"\nTOTAL {len(res)}  PASS {sum(res)}  FAIL {len(res)-sum(res)}")
