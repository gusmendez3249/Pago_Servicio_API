/// Validadores del formulario de login (devuelven null si el valor es válido).
class ValidadoresLogin {
  const ValidadoresLogin._();

  static final RegExp _patronUsuario = RegExp(r'^[A-Za-z0-9._-]+$');

  static String? usuario(String? valor) {
    final texto = valor?.trim() ?? '';
    if (texto.isEmpty) return 'El usuario es obligatorio.';
    if (texto.length < 3 || texto.length > 50) {
      return 'El usuario debe tener entre 3 y 50 caracteres.';
    }
    if (!_patronUsuario.hasMatch(texto)) {
      return 'Solo se permiten letras, números, punto, guion y guion bajo.';
    }
    return null;
  }

  static String? password(String? valor) {
    final texto = valor ?? '';
    if (texto.isEmpty) return 'La contraseña es obligatoria.';
    if (texto.length < 8 || texto.length > 72) {
      return 'La contraseña debe tener entre 8 y 72 caracteres.';
    }
    return null;
  }
}
