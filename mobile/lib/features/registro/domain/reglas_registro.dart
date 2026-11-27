/// Reglas del registro, idénticas a las del backend (DatosPersonalesDto,
/// LoginCredencialesDto e identidad congruente con la CURP). La pantalla
/// avisa antes de enviar; el backend vuelve a validar todo.
library;

const String _letras = 'a-zA-ZáéíóúÁÉÍÓÚüÜñÑ';
const String _mesDia = '(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])';
const String _entidades =
    '(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)';

final RegExp patronNombre = RegExp('^[$_letras]+( [$_letras]+)*\$');
final RegExp patronCurp = RegExp(
  '^[A-Z]{4}[0-9]{2}$_mesDia[HM]$_entidades[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]\$',
);
final RegExp patronRfc = RegExp('^[A-ZÑ&]{3,4}[0-9]{2}$_mesDia[A-Z0-9]{3}\$');
final RegExp patronCorreo = RegExp(
  r'^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$',
);
final RegExp patronUsuario = RegExp(r'^[A-Za-z0-9._-]{3,50}$');
final RegExp patronTextoSeguro = RegExp(r'^[^<>\x00-\x1f\x7f]*$');

const String _diccionarioCurp = '0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ';

bool nombreValido(String valor) {
  final v = valor.trim();
  return v.length >= 3 && v.length <= 50 && patronNombre.hasMatch(v);
}

/// Dígito verificador oficial de la CURP.
bool digitoCurpValido(String curp) {
  if (curp.length != 18) return false;
  var suma = 0;
  for (var i = 0; i < 17; i++) {
    final valor = _diccionarioCurp.indexOf(curp[i]);
    if (valor < 0) return false;
    suma += valor * (18 - i);
  }
  return curp[17] == '${(10 - suma % 10) % 10}';
}

String _aammdd(DateTime f) => '${(f.year % 100).toString().padLeft(2, '0')}'
    '${f.month.toString().padLeft(2, '0')}'
    '${f.day.toString().padLeft(2, '0')}';

enum ErrorCurp { formato, digito, fecha, sexo }

ErrorCurp? errorCurp(String curp, DateTime? nacimiento, String? sexo) {
  if (!patronCurp.hasMatch(curp)) return ErrorCurp.formato;
  if (!digitoCurpValido(curp)) return ErrorCurp.digito;
  if (nacimiento != null && curp.substring(4, 10) != _aammdd(nacimiento)) {
    return ErrorCurp.fecha;
  }
  final letra = curp[10];
  if ((sexo == 'MASCULINO' && letra != 'H') ||
      (sexo == 'FEMENINO' && letra != 'M')) {
    return ErrorCurp.sexo;
  }
  return null;
}

enum ErrorRfc { formato, generico, curp }

ErrorRfc? errorRfc(String rfc, String curp) {
  if (!patronRfc.hasMatch(rfc)) return ErrorRfc.formato;
  if (rfc.startsWith('XAXX') || rfc.startsWith('XEXX')) {
    return ErrorRfc.generico;
  }
  if (curp.length >= 10) {
    final inicio = rfc.length == 13 ? 4 : 3;
    if (rfc[0] != curp[0] ||
        rfc.substring(inicio, inicio + 6) != curp.substring(4, 10)) {
      return ErrorRfc.curp;
    }
  }
  return null;
}

/// Años cumplidos a [hoy].
int edad(DateTime nacimiento, DateTime hoy) {
  var anios = hoy.year - nacimiento.year;
  if (hoy.month < nacimiento.month ||
      (hoy.month == nacimiento.month && hoy.day < nacimiento.day)) {
    anios--;
  }
  return anios;
}

/// Misma política que el backend: 8 a 72, mayúscula, minúscula, número y
/// carácter especial, sin espacios.
bool passwordValida(String v) {
  return v.length >= 8 &&
      v.length <= 72 &&
      RegExp('[a-z]').hasMatch(v) &&
      RegExp('[A-Z]').hasMatch(v) &&
      RegExp('[0-9]').hasMatch(v) &&
      RegExp(r'[!-/:-@\[-`{-~]').hasMatch(v) &&
      !RegExp(r'[\s\x00-\x1f\x7f]').hasMatch(v);
}

/// PIN trivial: todos iguales (1111) o consecutivos (1234, 4321).
bool pinTrivial(String pin) {
  if (!RegExp(r'^\d{4}$').hasMatch(pin)) return false;
  final d = pin.split('').map(int.parse).toList();
  bool todos(bool Function(int i) f) =>
      [for (var i = 1; i < 4; i++) f(i)].every((x) => x);
  return todos((i) => d[i] == d[0]) ||
      todos((i) => d[i] == d[i - 1] + 1) ||
      todos((i) => d[i] == d[i - 1] - 1);
}
