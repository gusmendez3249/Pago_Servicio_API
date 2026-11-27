/// Clasificación de los fallos que puede devolver [ApiClient].
enum TipoFallo {
  validacion,
  noAutenticado,
  prohibido,
  noEncontrado,
  bloqueado,
  demasiadosIntentos,
  servidor,
  red,
  desconocido,
}

/// Error de validación asociado a un campo (`codigo == 1`).
class CampoError {
  const CampoError({required this.campo, required this.mensaje});

  final String campo;
  final String mensaje;
}

/// Resultado de una operación: éxito ([Ok]) o fallo ([Failure]). Nunca se lanza.
sealed class Result<T> {
  const Result();
}

final class Ok<T> extends Result<T> {
  const Ok(this.data, {this.mensaje = '', this.httpStatus = 200});

  final T data;
  final String mensaje;
  final int httpStatus;
}

final class Failure extends Result<Never> {
  const Failure({
    required this.tipo,
    required this.mensaje,
    this.httpStatus,
    this.codigo,
    this.campos = const [],
    this.retryAfterSegundos,
    this.data,
  });

  final TipoFallo tipo;
  final int? httpStatus;
  final int? codigo;
  final String mensaje;
  final List<CampoError> campos;
  final int? retryAfterSegundos;

  /// `data` del envoltorio de error (p. ej. `{motivo: ...}` o un
  /// `PagoServicioResponse` en un 422). Solo para lógica; nunca se muestra.
  final Object? data;
}
