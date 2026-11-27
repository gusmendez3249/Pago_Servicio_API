import '../../catalogo/domain/utils/formato_catalogo.dart';
import '../domain/entities/comprobante_pago.dart';
import '../domain/utils/reglas_pago.dart';

/// Textos EXACTOS de la especificación del Sprint 3. La pantalla muestra solo
/// estos: nunca texto del servidor ni términos técnicos.
abstract final class TextosPago {
  // Exitoso
  static const String exitosoTitulo = '¡Pago realizado!';
  static String exitosoMensaje({
    required String servicio,
    required double total,
    String? folio,
  }) {
    final base =
        'Tu pago de $servicio por ${formatearMoneda(total)} se aplicó correctamente.';
    return (folio == null || folio.isEmpty) ? base : '$base Folio: $folio.';
  }

  static const String verComprobante = 'Ver comprobante';
  static const String irInicio = 'Ir al inicio';
  static const String verMisPagos = 'Ver mis pagos';

  // Pendiente
  static const String pendienteTitulo = 'Estamos confirmando tu pago';
  static String pendienteMensaje(String? upc) {
    const base =
        'Aún no recibimos la confirmación de la operación. El importe está '
        'apartado de tu cuenta. No vuelvas a pagar: si no se aplica, te lo '
        'devolvemos.';
    return (upc == null || upc.isEmpty)
        ? base
        : '$base Número de operación: $upc.';
  }

  static const String consultarDeNuevo = 'Consultar de nuevo';
  static const String reintentarPago = 'Reintentar pago';

  // El pago no llegó (tres consultas por llave sin rastro).
  static const String noLlegoTitulo = 'No recibimos tu pago';
  static const String noLlegoMensaje =
      'Tu pago no llegó a procesarse. Puedes reintentarlo: no se hará un '
      'cargo doble.';

  // Sin conexión
  static const String sinConexionTitulo = 'Sin conexión';
  static const String sinConexionMensaje =
      'No pudimos comunicarnos. Revisa tu internet. Si ya confirmaste el '
      'pago, no lo repitas: lo estamos verificando.';

  // Rechazado
  static const String rechazadoTitulo = 'No se realizó el pago';
  static const String errorGestoPagoTitulo = 'Error de Gesto Pago';
  static const String errorGestoPago =
      'Gesto Pago, el proveedor del servicio, tuvo un error y no pudo procesar '
      'tu pago. Inténtalo más tarde.';
  static const String sinCargo = 'No se hizo ningún cargo a tu cuenta.';
  static const String referenciaInvalida =
      'No se reconoció la referencia. Revísala en tu recibo.';
  static const String rechazadoProveedor = 'La operación fue rechazada.';
  static const String servicioNoDisponible =
      'El servicio no está disponible por ahora. Inténtalo más tarde.';
  static const String validarOtraReferencia = 'Validar otra referencia';
  static const String intentarDeNuevo = 'Intentar de nuevo';
  static const String entendido = 'Entendido';

  // PIN
  static String pinIncorrecto(int? intentos) {
    if (intentos == null || intentos <= 0) {
      return 'PIN incorrecto. Inténtalo de nuevo.';
    }
    if (intentos == 1) return 'PIN incorrecto. Te queda 1 intento.';
    return 'PIN incorrecto. Te quedan $intentos intentos.';
  }

  static const String pinBloqueadoTitulo = 'PIN bloqueado';
  static const String pinBloqueadoMensaje =
      'Por seguridad bloqueamos tu PIN por 15 minutos. No se hizo ningún '
      'cargo.';

  // Saldo
  static const String saldoInsuficienteTitulo = 'Saldo insuficiente';
  static String saldoInsuficienteMensaje(double saldo, double total) =>
      'Tu saldo disponible (${formatearMoneda(saldo)}) no cubre el total '
      '(${formatearMoneda(total)}).';
  static const String volver = 'Volver';

  // Validación expirada
  static const String validacionExpiradaTitulo = 'La verificación expiró';
  static const String validacionExpiradaMensaje =
      'Por seguridad, valida tu referencia otra vez para continuar.';
  static const String validarDeNuevo = 'Validar de nuevo';

  // Sin cuenta
  static const String sinCuentaTitulo = 'No encontramos una cuenta activa';
  static const String sinCuentaMensaje = 'No es posible pagar por ahora.';

  // Genérico
  static const String genericoTitulo = 'Algo salió mal';
  static const String genericoMensaje =
      'No pudimos procesar tu solicitud. Inténtalo de nuevo.';

  // Captura
  static const String telefonoInvalido = 'Ingresa un número de 10 dígitos.';
  static const String telefonoNoCoincide = 'Los números no coinciden.';
  static const String importeVacio = 'Ingresa el importe de tu recibo.';
  static const String importeFueraDeRango =
      'El importe debe estar entre \$1.00 y \$10,000.00.';

  static String errorTelefono(ErrorTelefono e) =>
      e == ErrorTelefono.formato ? telefonoInvalido : telefonoNoCoincide;

  static String errorImporte(ErrorImporte e) =>
      e == ErrorImporte.vacio ? importeVacio : importeFueraDeRango;

  // Pantallas
  static const String tituloPantalla = 'Pago';
  static const String preparando = 'Preparando tu pago…';
  static const String telefonoEtiqueta = 'Número de teléfono';
  static const String telefonoConfirmarEtiqueta =
      'Confirma el número de teléfono';
  static const String importeEtiqueta = 'Importe de tu recibo';
  static const String continuar = 'Continuar';
  static const String revisarPago = 'Revisar pago';
  static const String notaPin = 'Te pediremos tu PIN para confirmar.';
  static const String detallePago = 'Detalle del pago';
  static const String tuPago = 'Tu pago';
  static const String importeAPagar = 'Importe a pagar';
  static const String capturaTelefono = 'Captura el teléfono';
  static const String datosPago = 'Datos del pago';
  static const String revisaYConfirma = 'Revisa y confirma';
  static const String preparandoTitulo = 'Preparando tu pago';
  static const String consultandoEstado = 'Consultando el estado…';
  static const String pagaste = 'Pagaste';
  static const String volverAlResultado = 'Volver al resultado';
  static const String resumenTitulo = 'Resumen del pago';
  static const String pagar = 'Pagar';

  /// El botón dice lo que hace: "Pagar $350.00".
  static String pagarImporte(double total) =>
      '$pagar ${formatearMoneda(total)}';
  static const String vasAPagar = 'Vas a pagar';
  static const String procesando =
      'Procesando tu pago. No cierres esta pantalla.';
  static const String copiarFolio = 'Copiar folio';
  static const String copiarNumeroOperacion = 'Copiar número';
  static const String estadoExitoso = 'Pago realizado';
  static const String estadoPendiente = 'Pendiente de confirmar';
  static const String estadoRechazado = 'No aplicado';
  static const String cambiarDatos = 'Cambiar datos';
  static const String cancelar = 'Cancelar';
  static const String confirmarPago = 'Confirmar pago';
  static const String confirmaTuPago = 'Confirma tu pago';
  static const String ingresaPin = 'PIN de 4 dígitos';
  static const String pinEtiqueta = 'PIN';
  static const String totalEtiqueta = 'Total';
  static const String importeResumen = 'Importe';
  static const String cuentaCargo = 'Cuenta de cargo';
  static const String cuenta = 'Cuenta';
  static const String saldoDisponible = 'Saldo disponible';
  static const String saldoDespues = 'Saldo después';
  static const String copiar = 'Copiar';
  static const String copiado = 'Copiado';
  static const String codigoTarjeta = 'Código de tu tarjeta';

  /// Texto amigable de un rechazo según su motivo (mismos textos del flujo).
  static String motivoRechazo(MotivoPago? motivo) {
    switch (motivo) {
      case MotivoPago.referenciaInvalida:
        return referenciaInvalida;
      case MotivoPago.errorGestoPago:
        return errorGestoPago;
      case MotivoPago.servicioNoDisponible:
      case MotivoPago.proveedorNoDisponible:
        return servicioNoDisponible;
      default:
        return rechazadoProveedor;
    }
  }

  // Comprobante
  static const String comprobanteTitulo = 'Comprobante';
  static const String servicio = 'Servicio';
  static const String producto = 'Producto';
  static const String referencia = 'Referencia';
  static const String telefono = 'Teléfono';
  static const String folio = 'Folio';
  static const String numeroOperacion = 'Número de operación';
  static const String fechaHora = 'Fecha y hora';
  static const String estatus = 'Estatus';

  static String estatusTexto(EstatusPago e) {
    switch (e) {
      case EstatusPago.confirmada:
        return 'Exitoso';
      case EstatusPago.rechazada:
        return 'Rechazado';
      case EstatusPago.indeterminada:
      case EstatusPago.pendiente:
      case EstatusPago.desconocido:
        return 'Pendiente';
    }
  }
}
