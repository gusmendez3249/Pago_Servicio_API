import 'package:flutter_test/flutter_test.dart';
import 'package:gesto_pago_mobile/core/http/result.dart';
import 'package:gesto_pago_mobile/features/pago/data/models/pago_models.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/comprobante_pago.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/llamada_pago.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/salida_pago.dart';
import 'package:gesto_pago_mobile/features/pago/domain/entities/solicitud_pago.dart';
import 'package:gesto_pago_mobile/features/pago/domain/utils/interpretar_respuesta.dart';
import 'package:gesto_pago_mobile/features/pago/domain/utils/reglas_pago.dart';
import 'package:gesto_pago_mobile/features/pago/presentation/textos_pago.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/utils/guard_pago.dart';
import 'package:gesto_pago_mobile/features/referencia/domain/entities/validacion_referencia.dart';

import 'repositorio_falso.dart';

void main() {
  group('error de Gesto Pago', () {
    test('ERROR_GESTOPAGO se reconoce y tiene su propio mensaje', () {
      expect(MotivoPago.desdeTexto('ERROR_GESTOPAGO'), MotivoPago.errorGestoPago);
      expect(TextosPago.motivoRechazo(MotivoPago.errorGestoPago), TextosPago.errorGestoPago);
      expect(TextosPago.errorGestoPago, contains('Gesto Pago'));
    });
  });

  group('teléfono', () {
    test('10 dígitos que coinciden es válido', () {
      expect(validarTelefono('5512345678', '5512345678'), isNull);
    });
    test('menos o más de 10 dígitos, letras o vacío es formato', () {
      for (final t in ['', '551234567', '55123456789', '55123abc78']) {
        expect(validarTelefono(t, t), ErrorTelefono.formato, reason: t);
      }
    });
    test('no coinciden', () {
      expect(validarTelefono('5512345678', '5512345679'), ErrorTelefono.noCoincide);
    });
  });

  group('importe', () {
    test('límites 1.00 y 10000.00', () {
      expect(validarImporte('1').monto, 1.0);
      expect(validarImporte('1.00').monto, 1.0);
      expect(validarImporte('10000').monto, 10000.0);
      expect(validarImporte('10000.00').monto, 10000.0);
      expect(validarImporte('350.5').monto, 350.5);
    });
    test('fuera de rango o con más de 2 decimales', () {
      for (final t in ['0', '0.99', '10000.01', '10001', '1.234', '1.', 'abc', '-5']) {
        final r = validarImporte(t);
        expect(r.monto, isNull, reason: t);
        expect(r.error, ErrorImporte.fueraDeRango, reason: t);
      }
    });
    test('vacío', () {
      expect(validarImporte('  ').error, ErrorImporte.vacio);
    });
  });

  test('resumen: total, saldo después y suficiencia', () {
    final r = calcularResumen(total: 350.10, saldo: 1000);
    expect(r.saldoDespues, 649.9);
    expect(r.saldoSuficiente, isTrue);
    expect(calcularResumen(total: 100.10, saldo: 100.10).saldoSuficiente, isTrue);
    expect(calcularResumen(total: 100.11, saldo: 100.10).saldoSuficiente, isFalse);
  });

  test('intervalos de sondeo: 10, 20, 35, 50, 70, 90 y 120 s acumulados', () {
    expect(kIntervalosSondeo.map((d) => d.inSeconds), [10, 20, 35, 50, 70, 90, 120]);
    expect(
      esperasEntreConsultas(kIntervalosSondeo).map((d) => d.inSeconds),
      [10, 10, 15, 15, 20, 20, 30],
    );
  });

  test('PIN válido: exactamente 4 dígitos', () {
    expect(pinValido('1234'), isTrue);
    expect(pinValido('123'), isFalse);
    expect(pinValido('12345'), isFalse);
    expect(pinValido('12a4'), isFalse);
  });

  group('guard pagoPermitido', () {
    final ahora = DateTime(2026, 1, 1);
    DatosPagoReferencia datos({DateTime? expira}) => DatosPagoReferencia(
          idSer: 5,
          idProd: 6,
          referencia: 'A',
          idValidacion: 'u',
          saldo: 10,
          situacion: SituacionSaldo.adeudo,
          servicio: 'CFE',
          producto: 'Luz',
          expiraEn: expira ?? DateTime(2030),
        );

    bool permitido({int? tipoFront, double? precio, DatosPagoReferencia? d}) =>
        pagoPermitido(
          tipoFront: tipoFront,
          precio: precio,
          datos: d,
          idServicio: 5,
          idProducto: 6,
          ahora: ahora,
        );

    test('tipoFront 1 exige precio mayor a cero y no pide validación', () {
      expect(permitido(tipoFront: 1, precio: 100), isTrue);
      expect(permitido(tipoFront: 1, precio: 0), isFalse);
      expect(permitido(tipoFront: 1), isFalse);
    });
    test('tipoFront 2 exige validación vigente', () {
      expect(permitido(tipoFront: 2, d: datos()), isTrue);
      expect(permitido(tipoFront: 2), isFalse);
      expect(permitido(tipoFront: 2, d: datos(expira: DateTime(2020))), isFalse);
    });
  });

  group('interpretación de respuestas del pago', () {
    test('200 CONFIRMADA es éxito; 202 es pendiente', () {
      expect(interpretarPago(ok200(comprobante(EstatusPago.confirmada, folio: 'F1'))), isA<PagoConfirmado>());
      expect(interpretarPago(ok202(comprobante(EstatusPago.indeterminada))), isA<PagoPendiente>());
    });
    test('el éxito solo se concede con CONFIRMADA', () {
      expect(interpretarPago(ok200(comprobante(EstatusPago.pendiente))), isA<PagoPendiente>());
      expect(interpretarPago(ok200(comprobante(EstatusPago.desconocido))), isA<PagoPendiente>());
      expect(interpretarPago(const LlamadaOk(httpStatus: 200)), isA<PagoPendiente>());
      expect(interpretarPago(fallo(200)), isA<PagoPendiente>());
    });
    test('422 rechazado con motivo; desconocido cae en RECHAZADO_PROVEEDOR', () {
      final r = interpretarPago(
        fallo(422, comprobante: comprobante(EstatusPago.rechazada, motivo: MotivoPago.referenciaInvalida)),
      );
      expect((r as PagoRechazado).motivo, MotivoPago.referenciaInvalida);
      final d = interpretarPago(fallo(422));
      expect((d as PagoRechazado).motivo, MotivoPago.rechazadoProveedor);
    });
    test('5xx, 423, 428, 403 PIN, 400 saldo, 401 y otros', () {
      expect(
        (interpretarPago(fallo(503, motivo: MotivoPago.proveedorNoDisponible)) as PagoRechazado).motivo,
        MotivoPago.proveedorNoDisponible,
      );
      expect(interpretarPago(fallo(423, motivo: MotivoPago.pinBloqueado)), isA<PagoPinBloqueado>());
      expect(interpretarPago(fallo(428)), isA<PagoValidacionExpirada>());
      final pin = interpretarPago(fallo(403, motivo: MotivoPago.pinIncorrecto, intentos: 2));
      expect((pin as PagoPinIncorrecto).intentosRestantes, 2);
      expect(interpretarPago(fallo(400, motivo: MotivoPago.saldoInsuficienteCuenta)), isA<PagoSaldoInsuficiente>());
      expect(interpretarPago(fallo(401)), isA<PagoSesionInvalida>());
      for (final s in [400, 403, 404, 409]) {
        expect(interpretarPago(fallo(s)), isA<PagoErrorGenerico>(), reason: '$s');
      }
    });
    test('5xx sin motivo PROVEEDOR_NO_DISPONIBLE (y 504) es pendiente, sin "sin cargo"', () {
      for (final s in [500, 502, 503, 504]) {
        expect(interpretarPago(fallo(s)), isA<PagoPendiente>(), reason: '$s');
      }
      expect(interpretarPago(fallo(500, motivo: MotivoPago.rechazadoProveedor)), isA<PagoPendiente>());
      expect(interpretarPago(fallo(200)), isA<PagoPendiente>());
    });
    test('sin red o tiempo agotado es pendiente por red', () {
      final r = interpretarPago(sinRed);
      expect(r, isA<PagoPendiente>());
      expect((r as PagoPendiente).porRed, isTrue);
    });
    test('consulta: 404 y 401 se distinguen del resto', () {
      expect(interpretarConsulta(fallo(404)), isA<ConsultaNoEncontrada>());
      expect(interpretarConsulta(fallo(401)), isA<ConsultaSesionInvalida>());
      expect(interpretarConsulta(fallo(500)), isA<ConsultaFallida>());
      expect(interpretarConsulta(ok200(comprobante(EstatusPago.confirmada, folio: 'F'))), isA<ConsultaConfirmada>());
    });
  });

  group('modelos tolerantes', () {
    test('PagoServicioResponse con nulos y valores desconocidos no lanza', () {
      final c = comprobanteDesdeJson({'estatus': 'RARO', 'monto': '12.5', 'folio': ' '});
      expect(c!.estatus, EstatusPago.desconocido);
      expect(c.monto, 12.5);
      expect(c.folio, isNull);
      expect(comprobanteDesdeJson(null), isNull);
    });
    test('detalle de error', () {
      final d = DetalleErrorModel.desdeJson({'motivo': 'PIN_INCORRECTO', 'intentosRestantes': 2});
      expect(d.motivo, MotivoPago.pinIncorrecto);
      expect(d.intentosRestantes, 2);
      expect(DetalleErrorModel.desdeJson(null).motivo, isNull);
    });
    test('cuentas: solo ACTIVAS y usables', () {
      final l = cuentasDesdeJson([
        {'numCuenta': '695429041188', 'numCuentaEnmascarada': '********1188', 'saldo': 1000, 'estatus': 'ACTIVA'},
        {'numCuenta': '111111111111', 'saldo': 5, 'estatus': 'BLOQUEADA'},
        {'saldo': 1},
        'x',
      ]);
      expect(l, hasLength(1));
      expect(l.first.saldo, 1000.0);
    });
    test('solicitud: no envía campos que no corresponden', () {
      const tel = SolicitudPago(
        numCuenta: '695429041188', idSer: 1, idProd: 2, monto: 50,
        esReferencia: false, telefono: '5512345678', twoFA: '1234',
      );
      expect(solicitudAJson(tel).keys, containsAll(['telefono', 'twoFA']));
      expect(solicitudAJson(tel).containsKey('reference'), isFalse);
      expect(solicitudAJson(tel).containsKey('idValidacion'), isFalse);
      const ref = SolicitudPago(
        numCuenta: '695429041188', idSer: 1, idProd: 2, monto: 50,
        esReferencia: true, reference: 'ABC', idValidacion: 'u', twoFA: '1234',
      );
      expect(solicitudAJson(ref).containsKey('telefono'), isFalse);
      expect(solicitudAJson(ref)['reference'], 'ABC');
    });
  });

  group('textos exactos', () {
    test('PIN incorrecto', () {
      expect(TextosPago.pinIncorrecto(2), 'PIN incorrecto. Te quedan 2 intentos.');
      expect(TextosPago.pinIncorrecto(1), 'PIN incorrecto. Te queda 1 intento.');
      expect(TextosPago.pinIncorrecto(null), 'PIN incorrecto. Inténtalo de nuevo.');
      expect(TextosPago.pinIncorrecto(0), 'PIN incorrecto. Inténtalo de nuevo.');
      expect(TextosPago.pinIncorrecto(-1), 'PIN incorrecto. Inténtalo de nuevo.');
    });
    test('éxito, pendiente, saldo y captura', () {
      expect(
        TextosPago.exitosoMensaje(servicio: 'Telcel', total: 100, folio: 'F1'),
        'Tu pago de Telcel por \$100.00 se aplicó correctamente. Folio: F1.',
      );
      expect(
        TextosPago.pendienteMensaje('K3J9X0A7ZQ'),
        'Aún no recibimos la confirmación de la operación. El importe está apartado de tu cuenta. No vuelvas a pagar: si no se aplica, te lo devolvemos. Número de operación: K3J9X0A7ZQ.',
      );
      expect(TextosPago.pendienteMensaje(null).contains('Número de operación'), isFalse);
      expect(
        TextosPago.saldoInsuficienteMensaje(50, 100),
        'Tu saldo disponible (\$50.00) no cubre el total (\$100.00).',
      );
      expect(TextosPago.importeFueraDeRango, 'El importe debe estar entre \$1.00 y \$10,000.00.');
      expect(TextosPago.telefonoInvalido, 'Ingresa un número de 10 dígitos.');
      expect(TextosPago.telefonoNoCoincide, 'Los números no coinciden.');
    });
  });

  test('Failure conserva data del envoltorio', () {
    const f = Failure(tipo: TipoFallo.prohibido, mensaje: 'x', data: {'motivo': 'PIN_INCORRECTO'});
    expect((f.data as Map)['motivo'], 'PIN_INCORRECTO');
  });

  test('LlamadaFallo 2xx cuenta como éxito HTTP', () {
    expect(fallo(202).esExitoHttp, isTrue);
    expect(fallo(404).esExitoHttp, isFalse);
  });
}
