/// Datos mínimos que se persisten de la sesión.
class DatosSesionGuardada {
  const DatosSesionGuardada({
    required this.token,
    required this.username,
    required this.nombreCliente,
    required this.rol,
    required this.ultimaActividad,
  });

  final String token;
  final String username;
  final String nombreCliente;
  final String rol;
  final DateTime ultimaActividad;

  DatosSesionGuardada conActividad(DateTime actividad) => DatosSesionGuardada(
        token: token,
        username: username,
        nombreCliente: nombreCliente,
        rol: rol,
        ultimaActividad: actividad,
      );
}

/// Persistencia de la sesión. Se sustituye por una versión en memoria en pruebas.
abstract interface class AlmacenSesion {
  Future<DatosSesionGuardada?> leer();
  Future<void> guardar(DatosSesionGuardada datos);
  Future<void> borrar();
}

/// Almacén solo en memoria: la sesión se pierde al cerrar o recargar. Es el
/// que se usa en el navegador, donde el almacenamiento seguro escribiría el
/// token en localStorage.
class AlmacenSesionMemoria implements AlmacenSesion {
  DatosSesionGuardada? _datos;

  @override
  Future<DatosSesionGuardada?> leer() async => _datos;

  @override
  Future<void> guardar(DatosSesionGuardada datos) async => _datos = datos;

  @override
  Future<void> borrar() async => _datos = null;
}
