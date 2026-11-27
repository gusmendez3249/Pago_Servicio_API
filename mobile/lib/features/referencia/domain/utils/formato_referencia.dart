/// Longitud máxima de la referencia (contrato del backend).
const int kLongitudMaximaReferencia = 100;

final RegExp _patronReferencia = RegExp(r'^[A-Za-z0-9]{1,100}$');

/// Motivo por el que el formato de la referencia no es aceptable.
enum ErrorFormatoReferencia { vacia, invalida }

/// Quita los espacios al inicio y al final.
String recortarReferencia(String texto) => texto.trim();

/// Valida el formato (ya recortada): `^[A-Za-z0-9]{1,100}$`.
/// Devuelve null si es correcto.
ErrorFormatoReferencia? validarFormatoReferencia(String texto) {
  final limpia = recortarReferencia(texto);
  if (limpia.isEmpty) return ErrorFormatoReferencia.vacia;
  if (!_patronReferencia.hasMatch(limpia)) {
    return ErrorFormatoReferencia.invalida;
  }
  return null;
}
