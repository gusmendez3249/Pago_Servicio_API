"""
Flujo completo con sesión: registro con PIN, login, autorización por recurso, validaciones previas al pago,
bloqueo del PIN, pago con GestoPago, idempotencia, concurrencia con la misma llave y conciliación.

Uso:
    python -X utf8 pruebas_flujo_sesion.py https://pago-servicios-api.onrender.com
    python -X utf8 pruebas_flujo_sesion.py http://localhost:8081

Variables de entorno (o se piden al arrancar; la contraseña no se muestra ni se guarda):
    EJECUTIVO_USUARIO, EJECUTIVO_PASSWORD   ejecutivo configurado en el servidor
    PAGO_ID_SER, PAGO_ID_PROD               producto a pagar (por defecto 2284 / 14302: ABIB $100)
    PAGO_TELEFONO                           teléfono destino (10 dígitos) si el producto usa teléfono
    PAGO_REFERENCIA                         referencia si el producto usa referencia

- Crea DOS clientes de prueba (ocupación "Prueba automatizada") y al final el ejecutivo los da de baja.
- Hace UN solo pago contra GestoPago, enviado 5 veces en paralelo con la misma Idempotency-Key: debe cobrarse una vez.
  Antes de enviarlo pide confirmación (o PAGO_CONFIRMADO=s).
- El resultado se guarda en resultado_pruebas_flujo_sesion.txt, sin tokens, contraseñas ni PIN.
Solo usa la librería estándar de Python 3.10+.
"""
import getpass
import json
import os
import random
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8081").rstrip("/")
CLI, LOGIN, PAGO, CONC = "/api/v1/layaway/cliente", "/api/v1/auth/login", "/api/v1/pagos/servicio", "/api/v1/pagos/conciliacion"
SALIDA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "resultado_pruebas_flujo_sesion.txt")
lineas, resultados, secretos = [], [], set()


def log(texto=""):
    for s in secretos:
        texto = texto.replace(s, "<oculto>")
    print(texto)
    lineas.append(texto)


def req(metodo, ruta, cuerpo=None, token=None, llave=None, timeout=90):
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    if llave:
        h["Idempotency-Key"] = llave
    datos = None
    if cuerpo is not None:
        # ingresoMensual exige exactamente 2 decimales en el JSON
        datos = json.dumps(cuerpo).replace('"ingresoMensual": 30000.0', '"ingresoMensual": 30000.00').encode()
    r = urllib.request.Request(BASE + ruta, data=datos, method=metodo, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as x:
            return x.status, json.loads(x.read() or b"null")
    except urllib.error.HTTPError as e:
        t = e.read()
        try:
            return e.code, json.loads(t)
        except ValueError:
            return e.code, t.decode("utf-8", "replace")[:200]


def check(nombre, cond, detalle=None):
    resultados.append((nombre, bool(cond)))
    log(("OK  " if cond else "XX  ") + nombre + ("" if cond or detalle is None else f"  :: {str(detalle)[:300]}"))


def consonantes(n):
    return "".join(random.choice("BCDFGHJKLMNPQRSTVWXZ") for _ in range(n))


def pin_aleatorio():
    while True:
        p = "".join(random.choice("0123456789") for _ in range(4))
        d = [int(c) for c in p]
        pasos = {d[i + 1] - d[i] for i in range(3)}
        if pasos not in ({0}, {1}, {-1}):
            return p


def cliente(username, pin, password):
    l4 = consonantes(4)
    return {"bandera": 1,
            "datosPersonales": {"nombre": "Prueba", "apellidoPaterno": "Flujo", "apellidoMaterno": "Sesion",
                                "fechaNacimiento": "1995-05-15", "curp": f"{l4}950515HDF{consonantes(3)}0{random.randint(0, 9)}",
                                "rfc": f"{l4}950515{random.randint(100, 999)}", "sexo": "MASCULINO",
                                "nacionalidad": "MEXICANA", "estadoCivil": "SOLTERO"},
            "datosContacto": {"correo": f"{username}@example.com", "telefonoMovil": 5512345678},
            "domicilio": {"calle": "Av. Reforma", "numeroExterior": "100", "colonia": "Centro",
                          "municipio": "Cuauhtemoc", "estado": "CDMX", "codigoPostal": "06000", "pais": "Mexico"},
            "informacionLaboral": {"ocupacion": "Prueba automatizada", "empresa": "UTNG", "ingresoMensual": 30000.00},
            "loginCredenciales": {"username": username, "password": password, "pin": pin}}


def token_de(usuario, password):
    st, b = req("POST", LOGIN, {"bandera": 1, "username": usuario, "password": password})
    if st != 200:
        return st, None
    secretos.add(b["data"]["token"])
    return st, b["data"]["token"]


def saldo(token):
    st, b = req("POST", CLI, {"bandera": 4}, token)
    try:
        return Decimal(str(b["data"]["clientes"][0]["cuentas"][0]["saldo"]))
    except (KeyError, IndexError, TypeError):
        return None


def main():
    ej_usuario = os.environ.get("EJECUTIVO_USUARIO") or input("Usuario ejecutivo: ").strip()
    ej_password = os.environ.get("EJECUTIVO_PASSWORD") or getpass.getpass("Contraseña del ejecutivo (no se muestra): ")
    secretos.add(ej_password)
    log(f"Flujo con sesión contra {BASE}  ({time.strftime('%Y-%m-%d %H:%M:%S')})")

    # ------------------------------------------------------------ producto a pagar
    id_ser = int(os.environ.get("PAGO_ID_SER", "2284"))
    id_prod = int(os.environ.get("PAGO_ID_PROD", "14302"))
    log("\n[0] Catálogo (puede tardar si el servicio estaba dormido)")
    st, cat = req("GET", "/productos", timeout=180)
    productos = cat["data"] if st == 200 else []
    prod = next((p for p in productos if p["idServicio"] == id_ser and p["idProducto"] == id_prod), None)
    check(f"producto {id_ser}/{id_prod} existe en el catálogo", prod is not None, st)
    if prod is None:
        return
    abierto = next((p for p in productos if Decimal(str(p["precio"])) == 0), None)
    monto = Decimal(str(prod["precio"]))
    if monto == 0:
        monto = Decimal(os.environ.get("PAGO_MONTO", "10.00"))
    usa_ref = prod["tipoFront"] not in (1,) and bool(os.environ.get("PAGO_REFERENCIA"))
    destino = ({"esReferencia": True, "reference": os.environ["PAGO_REFERENCIA"]} if usa_ref
               else {"esReferencia": False, "telefono": os.environ.get("PAGO_TELEFONO", "5512345678")})
    log(f"    {prod['servicio']} / {prod['producto']}  monto={monto}  destino={destino}")

    # ------------------------------------------------------------ registro y login
    log("\n[1] Registro con PIN y login")
    sufijo = consonantes(5).lower()
    pin_a, pin_b, pin_b2 = pin_aleatorio(), pin_aleatorio(), pin_aleatorio()
    pwd = f"Prueba#{random.randint(1000, 9999)}x"
    secretos.update({pwd})
    A = cliente(f"flujo.a.{sufijo}", pin_a, pwd)
    B = cliente(f"flujo.b.{sufijo}", pin_b, pwd)
    st, ra = req("POST", CLI, A)
    check("registrar cliente A -> 201", st == 201, (st, ra))
    st, rb = req("POST", CLI, B)
    check("registrar cliente B -> 201", st == 201, (st, rb))
    if not (isinstance(ra, dict) and ra.get("data") and isinstance(rb, dict) and rb.get("data")):
        return
    cuenta_a, cuenta_b = ra["data"]["numeroCuenta"], rb["data"]["numeroCuenta"]
    check("la respuesta del registro no incluye PIN ni hash", pin_a not in json.dumps(ra) and "pbkdf2" not in json.dumps(ra))
    check("saldo inicial 1000.00", Decimal(str(ra["data"]["saldoInicial"])) == Decimal("1000.00"), ra["data"])
    st, b = req("POST", CLI, A)
    check("registro duplicado (misma CURP) -> 409", st == 409, (st, b))
    sin_pin = cliente(f"flujo.c.{sufijo}", pin_a, pwd)
    del sin_pin["loginCredenciales"]["pin"]
    st, b = req("POST", CLI, sin_pin)
    check("registro sin PIN -> 400", st == 400 and "pin" in json.dumps(b), (st, b))
    st, _ = token_de(A["loginCredenciales"]["username"], pwd + "z")
    check("login con contraseña incorrecta -> 401", st == 401, st)
    st, t_a = token_de(A["loginCredenciales"]["username"], pwd)
    check("login cliente A -> 200", st == 200, st)
    st, t_b = token_de(B["loginCredenciales"]["username"], pwd)
    check("login cliente B -> 200", st == 200, st)
    st, t_e = token_de(ej_usuario, ej_password)
    check("login ejecutivo -> 200", st == 200, st)
    if not (t_a and t_b and t_e):
        return

    # ------------------------------------------------------------ autorización por recurso
    log("\n[2] Autorización por recurso")
    st, b = req("POST", CLI, {"bandera": 4}, t_a)
    check("A consulta: solo ve su registro", st == 200 and cuenta_a in json.dumps(b) and cuenta_b not in json.dumps(b), (st, b))
    st, b = req("POST", CLI, {"bandera": 2, "filtros": {"numeroCuenta": cuenta_b},
                              "informacionLaboral": {"ocupacion": "Intruso", "empresa": "Ataque", "ingresoMensual": 30000.00}}, t_a)
    check("A intenta modificar a B -> 404", st == 404, (st, b))
    st, b = req("POST", CLI, {"bandera": 3, "filtros": {"numeroCuenta": cuenta_b}}, t_a)
    check("cliente intenta dar de baja -> 403", st == 403, (st, b))
    st, b = req("POST", CLI, {"bandera": 4, "filtros": {"numeroCuenta": cuenta_b}}, t_e)
    check("ejecutivo consulta a B -> 200", st == 200 and cuenta_b in json.dumps(b), (st, b))

    # ------------------------------------------------------------ validaciones previas (no llegan a GestoPago)
    log("\n[3] Validaciones antes de enviar a GestoPago (ninguna cobra)")

    def pagar(token, cuenta, pin, llave=None, **cambios):
        cuerpo = {"numCuenta": cuenta, "idSer": id_ser, "idProd": id_prod, "monto": float(monto), **destino, "twoFA": pin}
        cuerpo.update(cambios)
        return req("POST", PAGO, cuerpo, token, llave or str(uuid.uuid4()))

    st, b = pagar(None, cuenta_a, pin_a)
    check("pago sin token -> 401", st == 401, (st, b))
    st, b = req("POST", PAGO, {"numCuenta": cuenta_a, "idSer": id_ser, "idProd": id_prod, "monto": float(monto),
                               **destino, "twoFA": pin_a}, t_a)
    check("pago sin Idempotency-Key -> 400", st == 400, (st, b))
    if Decimal(str(prod["precio"])) > 0:
        st, b = pagar(t_a, cuenta_a, pin_a, monto=float(monto) - 1)
        check("monto distinto al precio del catálogo -> 400", st == 400, (st, b))
    st, b = pagar(t_a, cuenta_a, pin_a, idProd=999999)
    check("producto inexistente -> 404", st == 404, (st, b))
    st, b = pagar(t_a, cuenta_b, pin_a)
    check("pagar con la cuenta de otro cliente -> 404", st == 404, (st, b))
    st, b = pagar(t_e, cuenta_a, pin_a)
    check("el ejecutivo no puede pagar por un cliente -> 403", st == 403, (st, b))
    if abierto:
        st, b = req("POST", PAGO, {"numCuenta": cuenta_a, "idSer": abierto["idServicio"], "idProd": abierto["idProducto"],
                                   "monto": 5000.00, "esReferencia": True, "reference": "0012345678", "twoFA": pin_a},
                    t_a, str(uuid.uuid4()))
        check("saldo insuficiente -> 400", st == 400 and "saldo" in json.dumps(b, ensure_ascii=False).lower(), (st, b))
    check("ninguna validación tocó el saldo de A", saldo(t_a) == Decimal("1000.00"), saldo(t_a))

    # ------------------------------------------------------------ PIN y bloqueo (cliente B)
    log("\n[4] PIN 2FA y bloqueo por intentos (cliente B)")
    malos = [p for p in ("1357", "2468", "9753", "8642") if p != pin_b][:3]
    codigos = [pagar(t_b, cuenta_b, p)[0] for p in malos]
    check("3 PIN incorrectos -> 403, 403, 423", codigos == [403, 403, 423], codigos)
    st, b = pagar(t_b, cuenta_b, pin_b)
    check("PIN correcto durante el bloqueo -> 423", st == 423, (st, b))
    st, b = req("POST", CLI, {"bandera": 2, "filtros": {"numeroCuenta": cuenta_b},
                              "loginCredenciales": {"username": B["loginCredenciales"]["username"], "pin": pin_b2}}, t_b)
    check("B cambia su PIN -> 200", st == 200, (st, b))
    # Con el PIN nuevo y un producto inexistente: si el PIN se acepta, el error es del catálogo (404), no 423/403
    st, b = pagar(t_b, cuenta_b, pin_b2, idProd=999999)
    check("con el PIN nuevo se levanta el bloqueo (llega a validar el producto)", st == 404, (st, b))
    st, b = pagar(t_b, cuenta_b, pin_b, idProd=999999)
    check("el PIN anterior ya no sirve -> 403", st == 403, (st, b))

    # ------------------------------------------------------------ pago con GestoPago + idempotencia
    log("\n[5] Pago con GestoPago: 5 envíos simultáneos con la MISMA Idempotency-Key")
    if os.environ.get("PAGO_CONFIRMADO", "").lower() != "s":
        r = input(f"    Se enviará a GestoPago un pago de ${monto} ({prod['producto']}). ¿Continuar? (s/N): ")
        if r.strip().lower() != "s":
            log("    Pago omitido por el usuario.")
            return finalizar(t_e, cuenta_a, cuenta_b)
    llave = str(uuid.uuid4())
    respuestas = []
    hilos = [threading.Thread(target=lambda: respuestas.append(pagar(t_a, cuenta_a, pin_a, llave=llave))) for _ in range(5)]
    [h.start() for h in hilos]
    [h.join() for h in hilos]
    estados = sorted(st for st, _ in respuestas)
    log(f"    códigos HTTP de los 5 envíos: {estados}")
    datos = [b["data"] for st, b in respuestas if isinstance(b, dict) and b.get("data")]
    upcs = {d["upc"] for d in datos}
    principal = datos[0] if datos else None
    check("las respuestas con datos comparten un solo upc", len(upcs) <= 1 and principal is not None, respuestas[:2])
    if principal is None:
        log("    Ningún envío devolvió datos; revise los códigos anteriores.")
        return finalizar(t_e, cuenta_a, cuenta_b)
    estatus, upc = principal["estatus"], principal["upc"]
    log(f"    estatus={estatus} upc={upc} código={principal.get('codigoRespuesta')} mensaje={principal.get('mensajeRespuesta')}")
    s_despues = saldo(t_a)
    esperado = Decimal("1000.00") - (monto if estatus in ("CONFIRMADA", "INDETERMINADA", "PENDIENTE") else 0)
    check(f"se cobró UNA sola vez (saldo {s_despues}, esperado {esperado})", s_despues == esperado, s_despues)
    check("la respuesta enmascara la cuenta", principal["numCuenta"].startswith("*") and cuenta_a not in json.dumps(principal))

    st, b = pagar(t_a, cuenta_a, pin_a, llave=llave)
    mismo = isinstance(b, dict) and b.get("data") and b["data"]["upc"] == upc
    check("reenviar con la misma llave devuelve el mismo resultado", mismo or (estatus == "RECHAZADA" and st == 422), (st, b))
    check("el reenvío no vuelve a cobrar", saldo(t_a) == s_despues, saldo(t_a))
    st, b = pagar(t_a, cuenta_a, pin_a, llave=llave, telefono="5599999999") if not usa_ref else \
        pagar(t_a, cuenta_a, pin_a, llave=llave, reference="OTRAREF1")
    check("misma llave con otros datos -> 409", st == 409, (st, b))
    st, b = pagar(t_b, cuenta_b, pin_b2, llave=llave)
    check("la llave de A usada por B -> 409", st == 409, (st, b))

    # ------------------------------------------------------------ conciliación
    log("\n[6] Conciliación")
    st, b = req("POST", CONC, {"upc": upc, "aplicada": True}, t_a)
    check("un cliente no puede conciliar -> 403", st == 403, (st, b))
    st, b = req("POST", CONC, {"upc": "ZZZZZZZZZZ", "aplicada": True}, t_e)
    check("conciliar un upc inexistente -> 404", st == 404, (st, b))
    if estatus == "INDETERMINADA":
        log(f"    El pago {upc} quedó INDETERMINADO: el monto sigue retenido. Verifíquelo en el portal de GestoPago y")
        log("    concílielo con POST /api/v1/pagos/conciliacion como ejecutivo (aplicada=true o false).")
    else:
        st, b = req("POST", CONC, {"upc": upc, "aplicada": False}, t_e)
        check(f"conciliar un pago {estatus} (ya resuelto) -> 409", st == 409, (st, b))

    # ------------------------------------------------------------ sesión
    log("\n[7] Sesión")
    nueva = f"Otra#{random.randint(1000, 9999)}x"
    secretos.add(nueva)
    st, b = req("POST", CLI, {"bandera": 2, "filtros": {"numeroCuenta": cuenta_a},
                              "loginCredenciales": {"username": A["loginCredenciales"]["username"], "password": nueva}}, t_a)
    st2, _ = req("POST", CLI, {"bandera": 4}, t_a)
    check("cambiar la contraseña invalida el token anterior", st == 200 and st2 == 401, (st, st2))
    finalizar(t_e, cuenta_a, cuenta_b, B["loginCredenciales"]["username"], pwd)


def finalizar(t_e, cuenta_a, cuenta_b, usuario_b=None, pwd=None):
    log("\n[8] Limpieza: el ejecutivo da de baja a los clientes de prueba")
    for c in (cuenta_a, cuenta_b):
        st, b = req("POST", CLI, {"bandera": 3, "filtros": {"numeroCuenta": c}}, t_e)
        check(f"baja del cliente de prueba ****{c[-4:]} -> 200", st == 200, (st, b))
    if usuario_b:
        st, _ = token_de(usuario_b, pwd)
        check("un cliente dado de baja ya no puede iniciar sesión", st in (401, 403), st)


if __name__ == "__main__":
    try:
        main()
    finally:
        ok = sum(1 for _, v in resultados if v)
        log(f"\n==== {ok}/{len(resultados)} como se esperaba ====")
        for nombre, v in resultados:
            if not v:
                log("  XX " + nombre)
        with open(SALIDA, "w", encoding="utf-8") as f:
            f.write("\n".join(lineas) + "\n")
        print(f"\nResultado guardado en {SALIDA}")
