import 'package:uuid/uuid.dart';

const Uuid _uuid = Uuid();

/// Genera una clave de idempotencia (UUID v4) para peticiones de pago.
String nuevaIdempotencyKey() => _uuid.v4();
