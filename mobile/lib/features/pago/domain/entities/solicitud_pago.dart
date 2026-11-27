/// Datos para ejecutar un pago. Contiene el PIN: se construye al enviar y se
/// descarta; nunca se guarda en el estado ni en almacenamiento.
class SolicitudPago {
  const SolicitudPago({
    required this.numCuenta,
    required this.idSer,
    required this.idProd,
    required this.monto,
    required this.esReferencia,
    required this.twoFA,
    this.telefono,
    this.reference,
    this.idValidacion,
  });

  final String numCuenta;
  final int idSer;
  final int idProd;
  final double monto;
  final bool esReferencia;
  final String twoFA;
  final String? telefono;
  final String? reference;
  final String? idValidacion;
}
