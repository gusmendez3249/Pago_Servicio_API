import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/features/referencia/data/models/validacion_referencia_model.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/entities/validacion_referencia.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/utils/guard_pago.dart';
import 'package:gesto_pago_mobile/features/referencia/presentation/textos_referencia.dart';

DatosPagoReferencia _datos({
  int idSer = 1,
  int idProd = 2,
  String idValidacion = 'abc',
  required DateTime expiraEn,
}) {
  return DatosPagoReferencia(
    idSer: idSer,
    idProd: idProd,
    referencia: '123',
    idValidacion: idValidacion,
    saldo: 10,
    situacion: SituacionSaldo.adeudo,
    servicio: 'CFE',
    producto: 'Luz',
    expiraEn: expiraEn,
  );
}

void main() {
  final ahora = DateTime(2026, 1, 1, 12);

  group('guard de la ruta de pago', () {
    test('sin validacion no pasa', () {
      expect(
        validacionVigenteParaPago(null,
            idServicio: 1, idProducto: 2, ahora: ahora),
        isFalse,
      );
    });

    test('vigente y mismo producto pasa', () {
      final d = _datos(expiraEn: ahora.add(const Duration(minutes: 1)));
      expect(
        validacionVigenteParaPago(d,
            idServicio: 1, idProducto: 2, ahora: ahora),
        isTrue,
      );
    });

    test('expirada no pasa', () {
      final d = _datos(expiraEn: ahora);
      expect(
        validacionVigenteParaPago(d,
            idServicio: 1, idProducto: 2, ahora: ahora),
        isFalse,
      );
    });

    test('otro producto no pasa', () {
      final d = _datos(expiraEn: ahora.add(const Duration(minutes: 1)));
      expect(
        validacionVigenteParaPago(d,
            idServicio: 1, idProducto: 3, ahora: ahora),
        isFalse,
      );
      expect(
        validacionVigenteParaPago(d,
            idServicio: 9, idProducto: 2, ahora: ahora),
        isFalse,
      );
    });

    test('idValidacion vacio no pasa', () {
      final d = _datos(
        idValidacion: '',
        expiraEn: ahora.add(const Duration(minutes: 1)),
      );
      expect(
        validacionVigenteParaPago(d,
            idServicio: 1, idProducto: 2, ahora: ahora),
        isFalse,
      );
    });

    test('solo tipoFront 2 y 4 admiten referencia', () {
      expect(tipoFrontAdmiteReferencia(2), isTrue);
      expect(tipoFrontAdmiteReferencia(4), isTrue);
      expect(tipoFrontAdmiteReferencia(1), isFalse);
      expect(tipoFrontAdmiteReferencia(null), isFalse);
    });
  });

  group('modelo tolerante', () {
    test('respuesta valida completa', () {
      final m = ValidacionReferenciaModel.fromJson({
        'estado': 'VALIDA',
        'motivo': null,
        'situacion': 'ADEUDO',
        'saldo': 350,
        'idValidacion': 'u-1',
        'vigenciaSegundos': 300,
        'puedePagar': true,
        'idSer': 1,
        'idProd': 2,
        'referencia': '0012345678',
        'servicio': 'CFE',
        'producto': 'Luz',
      }).entidad;
      expect(m.estado, EstadoValidacion.valida);
      expect(m.situacion, SituacionSaldo.adeudo);
      expect(m.saldo, 350.0);
      expect(m.idValidacion, 'u-1');
      expect(m.vigenciaSegundos, 300);
      expect(m.puedePagar, isTrue);
    });

    test('valores desconocidos y nulos no lanzan', () {
      final m = ValidacionReferenciaModel.fromJson({
        'estado': 'OTRO',
        'motivo': 'NUEVO',
        'situacion': 'RARA',
      }).entidad;
      expect(m.estado, EstadoValidacion.desconocido);
      expect(m.motivo, MotivoValidacion.desconocido);
      expect(m.situacion, SituacionSaldo.desconocida);
      expect(m.saldo, isNull);
      expect(m.idValidacion, isNull);
      expect(m.puedePagar, isFalse);
    });

    test('verificacion y montoAPagar', () {
      final a = ValidacionReferenciaModel.fromJson({
        'estado': 'VALIDA',
        'verificacion': 'SOLO_FORMATO',
        'montoAPagar': null,
      }).entidad;
      expect(a.verificacion, VerificacionReferencia.soloFormato);
      expect(a.montoAPagar, isNull);

      final b = ValidacionReferenciaModel.fromJson({
        'estado': 'VALIDA',
        'verificacion': 'EN_LINEA',
        'montoAPagar': 350.5,
      }).entidad;
      expect(b.verificacion, VerificacionReferencia.enLinea);
      expect(b.montoAPagar, 350.5);

      final c = ValidacionReferenciaModel.fromJson({
        'estado': 'VALIDA',
        'verificacion': 'ALGO_NUEVO',
      }).entidad;
      expect(c.verificacion, VerificacionReferencia.enLinea);
      final d = ValidacionReferenciaModel.fromJson({'estado': 'VALIDA'}).entidad;
      expect(d.verificacion, VerificacionReferencia.enLinea);
    });

    test('invalida', () {
      final m = ValidacionReferenciaModel.fromJson({
        'estado': 'INVALIDA',
        'motivo': 'REFERENCIA_INVALIDA',
        'puedePagar': false,
      }).entidad;
      expect(m.estado, EstadoValidacion.invalida);
      expect(m.motivo, MotivoValidacion.referenciaInvalida);
    });

    test('situacion se deduce del saldo si falta', () {
      const r = ValidacionReferencia(
        estado: EstadoValidacion.valida,
        saldo: -50,
      );
      expect(r.situacionEfectiva, SituacionSaldo.saldoAFavor);
    });
  });

  group('textos exactos', () {
    test('adeudo, saldo a favor (valor absoluto) y sin adeudo', () {
      expect(TextosReferencia.adeudo(350),
          'Referencia verificada. Tienes un adeudo de \$350.00.');
      expect(TextosReferencia.saldoAFavor(-50),
          'Referencia verificada. Tienes un saldo a favor de \$50.00.');
      expect(TextosReferencia.sinAdeudo,
          'Referencia verificada. No tienes adeudo pendiente.');
    });

    test('textos nuevos', () {
      expect(
        TextosReferencia.soloFormato,
        'No podemos consultar el adeudo de este servicio. Revisa que tu '
        'referencia sea correcta antes de continuar.',
      );
      expect(TextosReferencia.nadaPorPagar, 'No hay nada por pagar.');
      expect(TextosReferencia.totalAPagar(350), 'Total a pagar: \$350.00');
      expect(TextosReferencia.montoSiguientePaso,
          'El monto se captura en el siguiente paso');
    });

    test('mensajes fijos', () {
      expect(TextosReferencia.invalida,
          'No encontramos esa referencia. Revísala e inténtalo de nuevo.');
      expect(TextosReferencia.formato,
          'La referencia solo admite letras y números, sin espacios.');
      expect(TextosReferencia.vacia, 'Ingresa tu referencia.');
      expect(
        TextosReferencia.error,
        'No pudimos verificar tu referencia en este momento. Inténtalo más tarde.',
      );
      expect(
        TextosReferencia.expirada,
        'La verificación expiró. Valida tu referencia de nuevo para continuar.',
      );
      expect(TextosReferencia.soloConsulta,
          'Este servicio solo permite consultar el saldo.');
    });
  });
}
