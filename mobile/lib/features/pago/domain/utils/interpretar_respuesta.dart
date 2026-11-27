import '../../../../core/http/result.dart';
import '../entities/comprobante_pago.dart';
import '../entities/llamada_pago.dart';
import '../entities/salida_pago.dart';

/// Traduce la respuesta del pago a la transición de la máquina de estados.
///
/// El éxito solo se concede con estatus CONFIRMADA recibido del backend.
/// Cualquier duda sobre si el pago llegó a procesarse es PENDIENTE (nunca se
/// genera una llave nueva).
SalidaPago interpretarPago(LlamadaPago llamada) {
  switch (llamada) {
    case LlamadaOk(:final comprobante):
      if (comprobante == null) return const PagoPendiente();
      switch (comprobante.estatus) {
        case EstatusPago.confirmada:
          return PagoConfirmado(comprobante);
        case EstatusPago.rechazada:
          return PagoRechazado(
            _motivoDeRechazo(comprobante.motivo),
            comprobante: comprobante,
          );
        case EstatusPago.indeterminada:
        case EstatusPago.pendiente:
        case EstatusPago.desconocido:
          return PagoPendiente(comprobante: comprobante);
      }
    case LlamadaFallo():
      return _interpretarFallo(llamada);
  }
}

SalidaPago _interpretarFallo(LlamadaFallo f) {
  final status = f.httpStatus;
  // Sin respuesta del servidor (tiempo agotado o sin red) o respuesta 2xx
  // ilegible: no se sabe si el pago se procesó.
  if (f.tipo == TipoFallo.red || f.esExitoHttp) {
    return PagoPendiente(porRed: f.tipo == TipoFallo.red);
  }
  if (status == null) {
    return f.tipo == TipoFallo.desconocido
        ? const PagoPendiente()
        : const PagoErrorGenerico();
  }
  if (status == 401) return const PagoSesionInvalida();
  if (status == 423) return const PagoPinBloqueado();
  if (status == 428) return const PagoValidacionExpirada();
  if (status == 422) {
    return PagoRechazado(
      _motivoDeRechazo(f.comprobante?.motivo ?? f.motivo),
      comprobante: f.comprobante,
    );
  }
  if (status >= 500) {
    // Solo el motivo explícito garantiza que no hubo cargo. Cualquier otro
    // 5xx (incluido 504) es duda: pendiente, con la MISMA llave.
    if (f.motivo == MotivoPago.proveedorNoDisponible) {
      return const PagoRechazado(MotivoPago.proveedorNoDisponible);
    }
    return const PagoPendiente();
  }
  if (status == 403 && f.motivo == MotivoPago.pinIncorrecto) {
    return PagoPinIncorrecto(intentosRestantes: f.intentosRestantes);
  }
  if (status == 400 && f.motivo == MotivoPago.saldoInsuficienteCuenta) {
    return const PagoSaldoInsuficiente();
  }
  return const PagoErrorGenerico();
}

/// Un motivo ausente o desconocido se trata como rechazo del proveedor.
MotivoPago _motivoDeRechazo(MotivoPago? motivo) {
  switch (motivo) {
    case MotivoPago.referenciaInvalida:
    case MotivoPago.servicioNoDisponible:
    case MotivoPago.proveedorNoDisponible:
    case MotivoPago.rechazadoProveedor:
    case MotivoPago.errorGestoPago:
      return motivo!;
    default:
      return MotivoPago.rechazadoProveedor;
  }
}

/// Traduce la respuesta de `/pagos/estado`.
SalidaConsulta interpretarConsulta(LlamadaPago llamada) {
  switch (llamada) {
    case LlamadaOk(:final comprobante):
      if (comprobante == null) return const ConsultaPendiente(null);
      switch (comprobante.estatus) {
        case EstatusPago.confirmada:
          return ConsultaConfirmada(comprobante);
        case EstatusPago.rechazada:
          return ConsultaRechazada(
            _motivoDeRechazo(comprobante.motivo),
            comprobante,
          );
        case EstatusPago.indeterminada:
        case EstatusPago.pendiente:
        case EstatusPago.desconocido:
          return ConsultaPendiente(comprobante);
      }
    case LlamadaFallo(:final httpStatus):
      if (httpStatus == 401) return const ConsultaSesionInvalida();
      if (httpStatus == 404) return const ConsultaNoEncontrada();
      return const ConsultaFallida();
  }
}
