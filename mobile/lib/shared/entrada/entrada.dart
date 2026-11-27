import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart' show StringCharacters;

/// Qué puede escribir el usuario en cada tipo de campo. Son los mismos límites del backend
/// (DTOs con @Size/@Pattern) y de la web: lo que no cabe ahí no se puede teclear ni pegar.
enum Entrada {
  digitos(r'[0-9]', 10),
  nombre(r'[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ ]', 50, espaciosSimples: true),
  curp(r'[A-Z0-9]', 18, mayusculas: true),
  rfc(r'[A-ZÑ&0-9]', 13, mayusculas: true),
  correo(r'[A-Za-z0-9._%+@-]', 100),
  usuario(r'[A-Za-z0-9._-]', 50),
  password(r'[!-~À-ÿ]', 72),
  domicilio(r"[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ0-9 .,#'/-]", 100, espaciosSimples: true),
  numeroDomicilio(r'[A-Za-z0-9 /-]', 20,
      mayusculas: true, espaciosSimples: true),
  lugar(r'[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ .-]', 100, espaciosSimples: true),
  texto(r"[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ0-9 .,&()'/-]", 100, espaciosSimples: true),
  importe(r'[0-9.]', 9),
  alfanumerico(r'[A-Za-z0-9]', 100),
  busqueda(r"[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ0-9 .,&()'-]", 60, espaciosSimples: true);

  const Entrada(
    this.permitido,
    this.maximo, {
    this.mayusculas = false,
    this.espaciosSimples = false,
  });

  final String permitido;
  final int maximo;
  final bool mayusculas;
  final bool espaciosSimples;

  /// Formateador listo para `inputFormatters`.
  List<TextInputFormatter> formatos(
          {int? maximo, int? enteros, num? valorMaximo}) =>
      [
        FormatoEntrada(this,
            maximo: maximo, enteros: enteros, valorMaximo: valorMaximo),
      ];
}

/// Limpia un valor: quita lo no permitido, aplica mayúsculas y ajustes, y corta a la longitud máxima.
String limpiarEntrada(Entrada tipo, String valor, {int? maximo, int? enteros}) {
  final permitido = RegExp(tipo.permitido);
  var v = tipo.mayusculas ? valor.toUpperCase() : valor;
  v = v.characters.where(permitido.hasMatch).join();
  if (tipo == Entrada.importe) {
    v = _importe(v, enteros ?? 6);
  } else if (tipo == Entrada.correo) {
    final i = v.indexOf('@');
    if (i >= 0) {
      v = v.substring(0, i + 1) + v.substring(i + 1).replaceAll('@', '');
    }
  }
  if (tipo.espaciosSimples) {
    v = v.replaceFirst(RegExp(r'^ +'), '').replaceAll(RegExp(r' {2,}'), ' ');
  }
  final limite = maximo ?? tipo.maximo;
  final letras = v.characters;
  return letras.length > limite ? letras.take(limite).join() : v;
}

/// Solo dígitos y un punto, con 2 decimales como máximo.
String _importe(String v, int enteros) {
  final partes = v.split('.');
  var entero = partes.first.replaceFirst(RegExp(r'^0+(?=\d)'), '');
  if (entero.length > enteros) entero = entero.substring(0, enteros);
  if (partes.length == 1) return entero;
  final decimales = partes.skip(1).join();
  return '$entero.${decimales.length > 2 ? decimales.substring(0, 2) : decimales}';
}

/// Aplica [limpiarEntrada] a cada cambio (tecla, pegado o autocompletado) conservando el cursor.
class FormatoEntrada extends TextInputFormatter {
  FormatoEntrada(this.tipo, {this.maximo, this.enteros, this.valorMaximo});

  final Entrada tipo;
  final int? maximo;
  final int? enteros;

  /// Tope de un importe: si el valor nuevo lo rebasa se conserva el anterior.
  final num? valorMaximo;

  @override
  TextEditingValue formatEditUpdate(
      TextEditingValue oldValue, TextEditingValue newValue) {
    final limpio =
        limpiarEntrada(tipo, newValue.text, maximo: maximo, enteros: enteros);
    final tope = valorMaximo;
    if (tope != null &&
        limpio.isNotEmpty &&
        (num.tryParse(limpio) ?? 0) > tope) {
      return oldValue;
    }
    if (limpio == newValue.text) return newValue;
    final cursor = newValue.selection.isValid
        ? newValue.selection.extentOffset.clamp(0, newValue.text.length)
        : newValue.text.length;
    final antes = limpiarEntrada(tipo, newValue.text.substring(0, cursor),
        maximo: maximo, enteros: enteros);
    final posicion = antes.length.clamp(0, limpio.length);
    return TextEditingValue(
      text: limpio,
      selection: TextSelection.collapsed(offset: posicion),
    );
  }
}
