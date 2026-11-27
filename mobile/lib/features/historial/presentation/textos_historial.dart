/// Textos EXACTOS de la especificación del Sprint 4. Las pantallas muestran
/// solo estos: nunca texto del servidor, códigos ni términos técnicos.
abstract final class TextosHistorial {
  static const String titulo = 'Mis pagos';
  static const String tituloDetalle = 'Detalle del pago';
  static const String irAlCatalogo = 'Ir al catálogo';
  static const String catalogo = 'Catálogo';

  // Pestañas
  static const String todos = 'Todos';
  static const String exitosos = 'Exitosos';
  static const String rechazados = 'Rechazados';
  static const String pendientes = 'Pendientes';

  // Estados de la operación
  static const String exitoso = 'Exitoso';
  static const String rechazado = 'Rechazado';
  static const String pendiente = 'Pendiente';

  // Estados de la lista
  static const String vacioTitulo = 'Aún no tienes pagos';
  static const String vacioFiltro = 'No hay pagos con ese estado';
  static const String errorLista =
      'No pudimos cargar tus pagos. Inténtalo de nuevo.';
  static const String errorMas =
      'No pudimos cargar más pagos. Inténtalo de nuevo.';
  static const String reintentar = 'Reintentar';
  static const String verMas = 'Ver más';

  // Detalle
  static const String noEncontrada = 'No encontramos esa operación';
  static const String errorDetalle =
      'No pudimos cargar la operación. Inténtalo de nuevo.';
  static const String volverAMisPagos = 'Volver a mis pagos';
  static const String consultarDeNuevo = 'Consultar de nuevo';
  static const String pendienteDetalle =
      'Aún no recibimos la confirmación de esta operación. No vuelvas a pagar.';
}
