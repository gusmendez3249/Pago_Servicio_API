/// Cuenta del cliente desde la que se paga. El número completo vive solo en
/// memoria y nunca se muestra: en pantalla se usa [numCuentaEnmascarada].
class CuentaCliente {
  const CuentaCliente({
    required this.numCuenta,
    required this.numCuentaEnmascarada,
    required this.saldo,
  });

  final String numCuenta;
  final String numCuentaEnmascarada;
  final double saldo;
}
