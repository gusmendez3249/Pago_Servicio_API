import '../../../../core/http/json_helpers.dart';
import '../../domain/entities/validacion_referencia.dart';

/// Cuerpo de `POST /api/v1/pagos/referencia/validar`.
class ValidarReferenciaRequestModel {
  const ValidarReferenciaRequestModel({
    required this.idSer,
    required this.idProd,
    required this.referencia,
  });

  final int idSer;
  final int idProd;
  final String referencia;

  Map<String, Object?> toJson() => {
        'idSer': idSer,
        'idProd': idProd,
        'referencia': referencia,
      };
}

/// `data` de la respuesta; tolerante a nulos y a tipos distintos.
class ValidacionReferenciaModel {
  const ValidacionReferenciaModel({required this.entidad});

  final ValidacionReferencia entidad;

  factory ValidacionReferenciaModel.fromJson(Map<String, dynamic> json) {
    final motivoCrudo = aString(json['motivo']);
    final situacionCruda = aString(json['situacion']);
    return ValidacionReferenciaModel(
      entidad: ValidacionReferencia(
        estado: EstadoValidacion.desdeTexto(aString(json['estado'])),
        motivo: motivoCrudo == null
            ? null
            : MotivoValidacion.desdeTexto(motivoCrudo),
        situacion: situacionCruda == null
            ? null
            : SituacionSaldo.desdeTexto(situacionCruda),
        saldo: aDouble(json['saldo']),
        idValidacion: _textoNoVacio(json['idValidacion']),
        vigenciaSegundos: aInt(json['vigenciaSegundos']),
        puedePagar: aBool(json['puedePagar']) ?? false,
        idSer: aInt(json['idSer']),
        idProd: aInt(json['idProd']),
        referencia: aString(json['referencia']),
        servicio: aString(json['servicio']),
        producto: aString(json['producto']),
        verificacion:
            VerificacionReferencia.desdeTexto(aString(json['verificacion'])),
        montoAPagar: aDouble(json['montoAPagar']),
      ),
    );
  }

  static String? _textoNoVacio(Object? v) {
    final t = aString(v)?.trim();
    return (t == null || t.isEmpty) ? null : t;
  }
}
