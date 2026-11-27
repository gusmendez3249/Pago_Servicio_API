enum RolUsuario {
  cliente,
  ejecutivo;

  /// Un rol desconocido se trata como `cliente` (el de menos privilegios).
  static RolUsuario desdeTexto(String? valor) {
    switch (valor?.trim().toUpperCase()) {
      case 'EJECUTIVO':
        return RolUsuario.ejecutivo;
      default:
        return RolUsuario.cliente;
    }
  }

  String get etiqueta => this == RolUsuario.ejecutivo ? 'Ejecutivo' : 'Cliente';
}
